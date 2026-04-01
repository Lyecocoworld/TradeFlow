package com.github.lye.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.github.lye.redis.RedisClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * High-performance cache using Caffeine (L1) + Redis (L2).
 * <p>
 * Features:
 * <ul>
 *   <li>Size-based eviction (not just TTL)</li>
 *   <li>Async loading</li>
 *   <li>Statistics collection</li>
 *   <li>Automatic cleanup</li>
 * </ul>
 *
 * @param <K> key type
 * @param <V> value type
 * @author lye
 * @since 0.1
 */
public class CaffeineCache<K, V> {

    private static final Logger LOGGER = Logger.getLogger(CaffeineCache.class.getName());

    private final Cache<K, V> localCache;
    private final RedisClient redisClient;
    private final String cachePrefix;
    private final Function<K, String> keySerializer;
    private final Function<String, V> valueDeserializer;
    private final Function<V, String> valueSerializer;
    private final long redisTtlMillis;

    private CaffeineCache(Builder<K, V> builder) {
        this.redisClient = builder.redisClient;
        this.cachePrefix = builder.cachePrefix;
        this.keySerializer = builder.keySerializer;
        this.valueDeserializer = builder.valueDeserializer;
        this.valueSerializer = builder.valueSerializer;
        this.redisTtlMillis = builder.redisTtlMillis;

        // Build Caffeine cache with proper generic typing
        @SuppressWarnings("unchecked")
        Caffeine<K, V> caffeineBuilder = (Caffeine<K, V>) Caffeine.newBuilder()
                .executor(builder.asyncExecutor)
                .maximumSize(builder.maximumSize)
                .expireAfterWrite(builder.expireAfterWriteMillis, TimeUnit.MILLISECONDS);

        if (builder.recordStats) {
            caffeineBuilder.recordStats();
        }

        this.localCache = caffeineBuilder.build();
    }

    /**
     * Gets a value from cache, loading from source if necessary.
     *
     * @param key the cache key
     * @param loader the function to load the value if not cached
     * @return the cached value
     */
    public V get(K key, Function<K, V> loader) {
        // L1: Try local cache first (synchronous)
        V value = localCache.getIfPresent(key);
        if (value != null) {
            return value;
        }

        // L2: Try Redis cache
        String redisKey = cachePrefix + keySerializer.apply(key);
        String redisValue = redisClient.get(redisKey);
        if (redisValue != null) {
            value = valueDeserializer.apply(redisValue);
            if (value != null) {
                // Populate L1
                localCache.put(key, value);
                return value;
            }
        }

        // L3: Load from source and populate all levels
        value = loader.apply(key);
        if (value != null) {
            localCache.put(key, value);
            // Async write to Redis (don't block)
            redisClient.set(redisKey, valueSerializer.apply(value), redisTtlMillis);
        }

        return value;
    }

    /**
     * Gets a value from cache asynchronously.
     *
     * @param key the cache key
     * @param loader the function to load the value if not cached
     * @return a CompletableFuture with the cached value
     */
    public CompletableFuture<V> getAsync(K key, Function<K, V> loader) {
        // L1: Try local cache first
        V value = localCache.getIfPresent(key);
        if (value != null) {
            return CompletableFuture.completedFuture(value);
        }

        // L2: Try Redis cache (async via virtual thread)
        return CompletableFuture.supplyAsync(() -> {
            String redisKey = cachePrefix + keySerializer.apply(key);
            String redisValue = redisClient.get(redisKey);
            if (redisValue != null) {
                V parsed = valueDeserializer.apply(redisValue);
                if (parsed != null) {
                    localCache.put(key, parsed);
                    return parsed;
                }
            }
            // L3: Load from source
            V loaded = loader.apply(key);
            if (loaded != null) {
                localCache.put(key, loaded);
                redisClient.set(redisKey, valueSerializer.apply(loaded), redisTtlMillis);
            }
            return loaded;
        });
    }

    /**
     * Gets a value from cache without loading from source.
     *
     * @param key the cache key
     * @return the cached value, or null if not found
     */
    public V getIfPresent(K key) {
        V value = localCache.getIfPresent(key);
        if (value != null) {
            return value;
        }

        // Check Redis
        String redisKey = cachePrefix + keySerializer.apply(key);
        String redisValue = redisClient.get(redisKey);
        if (redisValue != null) {
            value = valueDeserializer.apply(redisValue);
            if (value != null) {
                localCache.put(key, value);
            }
        }

        return value;
    }

    /**
     * Puts a value into all cache levels.
     *
     * @param key the cache key
     * @param value the value to cache
     */
    public void put(K key, V value) {
        // L1: Update local cache
        localCache.put(key, value);

        // L2: Update Redis cache (async)
        String redisKey = cachePrefix + keySerializer.apply(key);
        redisClient.set(redisKey, valueSerializer.apply(value), redisTtlMillis);
    }

    /**
     * Puts a value asynchronously.
     *
     * @param key the cache key
     * @param value the value to cache
     * @return a CompletableFuture that completes when the value is cached
     */
    public CompletableFuture<Void> putAsync(K key, V value) {
        // L1: Update local cache
        localCache.put(key, value);

        // L2: Update Redis cache asynchronously
        return CompletableFuture.runAsync(() -> {
            String redisKey = cachePrefix + keySerializer.apply(key);
            redisClient.set(redisKey, valueSerializer.apply(value), redisTtlMillis);
        });
    }

    /**
     * Invalidates a value from all cache levels.
     *
     * @param key the cache key to invalidate
     */
    public void invalidate(K key) {
        // L1: Remove from local cache
        localCache.invalidate(key);

        // L2: Remove from Redis (expire immediately)
        String redisKey = cachePrefix + keySerializer.apply(key);
        redisClient.set(redisKey, "", 1);
    }

    /**
     * Invalidates all values from local cache.
     */
    public void invalidateAll() {
        localCache.invalidateAll();
    }

    /**
     * Performs scheduled maintenance on the cache.
     */
    public void cleanUp() {
        localCache.cleanUp();
    }

    /**
     * Gets the approximate size of the local cache.
     *
     * @return the local cache size
     */
    public long size() {
        return localCache.estimatedSize();
    }

    /**
     * Gets cache statistics if enabled.
     *
     * @return the cache statistics, or null if stats not enabled
     */
    public CacheStats getStats() {
        return localCache.stats();
    }

    /**
     * Creates a new builder for CaffeineCache.
     *
     * @param <K> key type
     * @param <V> value type
     * @return a new builder
     */
    public static <K, V> Builder<K, V> builder() {
        return new Builder<>();
    }

    /**
     * Builder for CaffeineCache.
     *
     * @param <K> key type
     * @param <V> value type
     */
    public static class Builder<K, V> {
        private RedisClient redisClient;
        private String cachePrefix = "tf:cache:";
        private Function<K, String> keySerializer = Object::toString;
        private Function<String, V> valueDeserializer;
        private Function<V, String> valueSerializer = Object::toString;
        private long expireAfterWriteMillis = 5000; // 5 seconds default
        private long redisTtlMillis = 60000; // 1 minute default
        private long maximumSize = 1000;
        private boolean recordStats = false;
        private Executor asyncExecutor = Runnable::run; // Synchronous by default

        public Builder<K, V> redisClient(RedisClient redisClient) {
            this.redisClient = redisClient;
            return this;
        }

        public Builder<K, V> cachePrefix(String cachePrefix) {
            this.cachePrefix = cachePrefix;
            return this;
        }

        public Builder<K, V> keySerializer(Function<K, String> keySerializer) {
            this.keySerializer = keySerializer;
            return this;
        }

        public Builder<K, V> valueSerializer(Function<V, String> valueSerializer) {
            this.valueSerializer = valueSerializer;
            return this;
        }

        public Builder<K, V> valueDeserializer(Function<String, V> valueDeserializer) {
            this.valueDeserializer = valueDeserializer;
            return this;
        }

        public Builder<K, V> localTtlMillis(long ttlMillis) {
            this.expireAfterWriteMillis = ttlMillis;
            return this;
        }

        public Builder<K, V> redisTtlMillis(long ttlMillis) {
            this.redisTtlMillis = ttlMillis;
            return this;
        }

        public Builder<K, V> maximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
            return this;
        }

        public Builder<K, V> recordStats(boolean recordStats) {
            this.recordStats = recordStats;
            return this;
        }

        public Builder<K, V> asyncExecutor(Executor asyncExecutor) {
            this.asyncExecutor = asyncExecutor;
            return this;
        }

        public CaffeineCache<K, V> build() {
            if (redisClient == null) {
                throw new IllegalStateException("redisClient is required");
            }
            if (valueDeserializer == null) {
                throw new IllegalStateException("valueDeserializer is required");
            }
            return new CaffeineCache<>(this);
        }
    }
}
