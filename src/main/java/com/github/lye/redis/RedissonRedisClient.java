package com.github.lye.redis;

import com.github.lye.resilience.CircuitBreaker;
import org.redisson.Redisson;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * RedisClient implementation backed by Redisson with Circuit Breaker protection.
 * <p>
 * Replaces the Jedis-based implementation with Redisson 3.24+,
 * providing native async support, connection pooling, and cluster-ready API.
 * Only implements the small subset of operations exposed by RedisClient
 * (cache + pub/sub + distributed locking primitives).</p>
 *
 * @author lye
 * @since 0.1
 */
public class RedissonRedisClient implements RedisClient {

    private final RedissonClient redisson;
    private final boolean enabled;
    private final CircuitBreaker circuitBreaker;
    private static final Logger LOGGER = Logger.getLogger(RedissonRedisClient.class.getName());

    /**
     * Lua script for atomic GET + conditional DELETE.
     * Only deletes the key if its current value matches the expected value.
     * This prevents accidentally deleting a lock acquired by another server.
     */
    private static final String LUA_GET_DEL_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    public RedissonRedisClient(String host, int port, String password, int database, boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            this.redisson = null;
            this.circuitBreaker = null;
            return;
        }

        // Initialize circuit breaker for Redis operations
        this.circuitBreaker = CircuitBreaker.builder()
                .name("redis")
                .failureThreshold(5)
                .openTimeout(30, TimeUnit.SECONDS)
                .halfOpenMaxCallDuration(5, TimeUnit.SECONDS)
                .build();

        Config config = new Config();
        String address = "redis://" + host + ":" + port;

        if (password != null && !password.isEmpty()) {
            config.useSingleServer()
                    .setAddress(address)
                    .setPassword(password)
                    .setDatabase(database)
                    .setConnectionPoolSize(8)
                    .setConnectionMinimumIdleSize(2)
                    .setConnectTimeout(5000)
                    .setIdleConnectionTimeout(10000);
        } else {
            config.useSingleServer()
                    .setAddress(address)
                    .setDatabase(database)
                    .setConnectionPoolSize(8)
                    .setConnectionMinimumIdleSize(2)
                    .setConnectTimeout(5000);
        }

        this.redisson = Redisson.create(config);
    }

    @Override
    public boolean isEnabled() {
        return enabled && redisson != null && !redisson.isShutdown();
    }

    @Override
    public void set(String key, String value, long ttlMillis) {
        if (!isEnabled()) {
            return;
        }

        try {
            circuitBreaker.execute(() -> {
                if (ttlMillis > 0) {
                    redisson.getBucket(key).set(value, ttlMillis, TimeUnit.MILLISECONDS);
                } else {
                    redisson.getBucket(key).set(value);
                }
                return null;
            });
        } catch (CircuitBreaker.CircuitBreakerOpenException e) {
            LOGGER.warning("Circuit breaker OPEN while setting key '" + key + "' - skipping Redis write");
        } catch (Exception e) {
            LOGGER.warning("Redis error while setting key '" + key + "': " + e.getMessage());
        }
    }

    @Override
    public String get(String key) {
        if (!isEnabled()) {
            return null;
        }

        try {
            return circuitBreaker.execute(() -> (String) redisson.getBucket(key).get());
        } catch (CircuitBreaker.CircuitBreakerOpenException e) {
            LOGGER.warning("Circuit breaker OPEN while getting key '" + key + "' - blocking Redis access");
            return null;
        } catch (Exception e) {
            LOGGER.warning("Redis error while getting key '" + key + "': " + e.getMessage());
            return null;
        }
    }

    @Override
    public void publish(String channel, String message) {
        if (!isEnabled()) {
            return;
        }

        try {
            circuitBreaker.execute(() -> {
                redisson.getTopic(channel).publish(message);
                return null;
            });
        } catch (CircuitBreaker.CircuitBreakerOpenException e) {
            LOGGER.warning("Circuit breaker OPEN while publishing to '" + channel + "' - skipping Redis publish");
        } catch (Exception e) {
            LOGGER.warning("Redis error while publishing to '" + channel + "': " + e.getMessage());
        }
    }

    @Override
    public void subscribe(String channel, RedisMessageListener listener) {
        if (!isEnabled()) {
            return;
        }

        try {
            redisson.getTopic(channel).addListener(String.class, (ch, msg) -> {
                try {
                    listener.onMessage(ch.toString(), msg);
                } catch (Exception e) {
                    LOGGER.warning("Error in Redis message handler for channel '" + ch + "': " + e.getMessage());
                }
            });
            LOGGER.info("Subscribed to Redis channel via Redisson: " + channel);
        } catch (Exception e) {
            LOGGER.warning("Redis error while subscribing to '" + channel + "': " + e.getMessage());
        }
    }

    /**
     * Atomic SET NX EX operation for distributed locking.
     * <p>
     * Uses Redisson's native {@code trySet()} which maps to the Redis
     * {@code SET key value NX EX seconds} command — fully atomic.</p>
     *
     * @param key        the lock key
     * @param value      the lock value (typically a unique identifier)
     * @param ttlSeconds time-to-live in seconds
     * @return true if the key was set (lock acquired), false otherwise
     */
    @Override
    public boolean setNxEx(String key, String value, long ttlSeconds) {
        if (!isEnabled()) {
            return false;
        }

        try {
            return circuitBreaker.execute(() ->
                    redisson.getBucket(key).trySet(value, ttlSeconds, TimeUnit.SECONDS)
            );
        } catch (CircuitBreaker.CircuitBreakerOpenException e) {
            LOGGER.warning("Circuit breaker OPEN while setting NX EX for key '" + key + "'");
            return false;
        } catch (Exception e) {
            LOGGER.warning("Redis error while setting NX EX for key '" + key + "': " + e.getMessage());
            return false;
        }
    }

    /**
     * Atomic GET and DELETE operation for safe distributed unlocking.
     * <p>
     * Uses a Lua script to ensure the lock is only deleted if this instance
     * still owns it. This prevents accidentally deleting a lock acquired by
     * another server.</p>
     *
     * @param key           the lock key
     * @param expectedValue the value we expect (our lock identifier)
     * @return true if the key was deleted, false otherwise
     */
    @Override
    public boolean getDel(String key, String expectedValue) {
        if (!isEnabled()) {
            return false;
        }

        try {
            return circuitBreaker.execute(() -> {
                Long result = redisson.getScript().eval(
                        RScript.Mode.READ_WRITE,
                        LUA_GET_DEL_SCRIPT,
                        RScript.ReturnType.INTEGER,
                        Collections.singletonList(key),
                        expectedValue
                );
                return result != null && result == 1L;
            });
        } catch (CircuitBreaker.CircuitBreakerOpenException e) {
            LOGGER.warning("Circuit breaker OPEN while getting/deleting key '" + key + "'");
            return false;
        } catch (Exception e) {
            LOGGER.warning("Redis error while getting/deleting key '" + key + "': " + e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        if (redisson != null && !redisson.isShutdown()) {
            redisson.shutdown();
        }
    }

    /**
     * Gets the circuit breaker for monitoring/management.
     *
     * @return the circuit breaker, or null if Redis is disabled
     */
    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    /**
     * Gets the current circuit breaker metrics.
     *
     * @return metrics string, or "Redis disabled" if not applicable
     */
    public String getCircuitBreakerMetrics() {
        if (circuitBreaker == null) {
            return "Redis disabled";
        }
        return circuitBreaker.getMetrics();
    }
}
