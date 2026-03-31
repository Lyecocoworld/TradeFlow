package com.github.lye.redis;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.bukkit.plugin.Plugin;

/**
 * Distributed lock implementation using Redis.
 * <p>
 * Prevents race conditions across multiple servers for critical operations
 * like price updates and stock modifications.</p>
 * <p>
 * Supports try-with-resources pattern for automatic cleanup.</p>
 * <p>
 * <b>Threading note:</b> The synchronous {@link #tryLock(long)} method uses
 * {@code Thread.sleep()} for retries and must <b>NOT</b> be called from a
 * Folia region thread. Use {@link #tryLockAsync(long, Plugin)} from region
 * threads instead.</p>
 *
 * @author lye
 * @since 0.1
 */
public class DistributedLock implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(DistributedLock.class.getName());
    private static final String LOCK_PREFIX = "tradeflow:lock:";
    private static final long DEFAULT_TIMEOUT_MS = 10000; // 10 seconds
    private static final long RETRY_DELAY_MS = 100;

    private final RedisClient redisClient;
    private final String lockKey;
    private final String lockValue;
    private final long timeoutMs;
    private volatile boolean locked = false;

    /**
     * Creates a new distributed lock.
     *
     * @param redisClient the Redis client
     * @param resourceName the resource to lock (e.g., "shop:DIAMOND", "price:recalculate")
     */
    public DistributedLock(RedisClient redisClient, String resourceName) {
        this(redisClient, resourceName, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Creates a new distributed lock with custom timeout.
     *
     * @param redisClient the Redis client
     * @param resourceName the resource to lock
     * @param timeoutMs lock timeout in milliseconds
     */
    public DistributedLock(RedisClient redisClient, String resourceName, long timeoutMs) {
        this.redisClient = redisClient;
        this.lockKey = LOCK_PREFIX + resourceName;
        this.lockValue = UUID.randomUUID().toString();
        this.timeoutMs = timeoutMs;
    }

    /**
     * Attempts to acquire the lock.
     *
     * @return true if lock was acquired, false otherwise
     */
    public boolean tryLock() {
        return tryLock(DEFAULT_TIMEOUT_MS);
    }

    /**
     * Attempts to acquire the lock with retries.
     * <p>
     * Uses atomic SET NX EX operation via Lua script to prevent race conditions.</p>
     * <p>
     * <b>WARNING:</b> This method uses {@code Thread.sleep()} for retries and must
     * <b>NOT</b> be called from a Folia region thread. It is safe to call from
     * async threads (e.g., Jedis subscriber threads, async scheduler tasks).
     * For region-thread callers, use {@link #tryLockAsync(long, Plugin)} instead.</p>
     *
     * @param maxWaitMs maximum time to wait for lock in milliseconds
     * @return true if lock was acquired, false otherwise
     */
    public boolean tryLock(long maxWaitMs) {
        long startTime = System.currentTimeMillis();
        long ttlSeconds = Math.max(1, timeoutMs / 1000);

        while (true) {
            // Use atomic SET NX EX command (Lua script) - no race condition!
            boolean acquired = redisClient.setNxEx(lockKey, lockValue, ttlSeconds);

            if (acquired) {
                locked = true;
                LOGGER.fine("Acquired distributed lock: " + lockKey);
                return true;
            }

            // Check if we've exceeded max wait time
            if (maxWaitMs > 0 && System.currentTimeMillis() - startTime >= maxWaitMs) {
                LOGGER.warning("Failed to acquire distributed lock: " + lockKey + " (timeout)");
                return false;
            }

            // Wait before retry
            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    /**
     * Async version of tryLock — safe to call from any thread, including Folia region threads.
     * <p>
     * Uses the Folia async scheduler for retries instead of {@code Thread.sleep()},
     * so it never blocks a region thread.</p>
     *
     * @param maxWaitMs maximum time to wait for lock in milliseconds
     * @param plugin    the plugin instance (required for scheduler access)
     * @return a {@link CompletableFuture} that completes with {@code true} if the lock
     *         was acquired, {@code false} on timeout
     */
    public CompletableFuture<Boolean> tryLockAsync(long maxWaitMs, Plugin plugin) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        attemptAcquireAsync(maxWaitMs, System.currentTimeMillis(), future, plugin);
        return future;
    }

    private void attemptAcquireAsync(long maxWaitMs, long startTime,
                                      CompletableFuture<Boolean> future, Plugin plugin) {
        long ttlSeconds = Math.max(1, timeoutMs / 1000);
        boolean acquired = redisClient.setNxEx(lockKey, lockValue, ttlSeconds);
        if (acquired) {
            locked = true;
            LOGGER.fine("Acquired distributed lock (async): " + lockKey);
            future.complete(true);
            return;
        }

        if (maxWaitMs > 0 && System.currentTimeMillis() - startTime >= maxWaitMs) {
            LOGGER.warning("Failed to acquire distributed lock (async): " + lockKey + " (timeout)");
            future.complete(false);
            return;
        }

        // Schedule retry on async scheduler instead of Thread.sleep
        plugin.getServer().getAsyncScheduler().runDelayed(plugin, task ->
                attemptAcquireAsync(maxWaitMs, startTime, future, plugin),
                RETRY_DELAY_MS, TimeUnit.MILLISECONDS
        );
    }

    /**
     * Releases the lock.
     * <p>
     * Uses atomic GET + DEL operation via Lua script to ensure we only release
     * if this instance still holds the lock (prevents accidental unlock by others).</p>
     */
    public void unlock() {
        if (!locked) {
            return;
        }

        try {
            // Atomic GET + DEL - only deletes if value matches our lock value
            boolean deleted = redisClient.getDel(lockKey, lockValue);
            if (deleted) {
                locked = false;
                LOGGER.fine("Released distributed lock: " + lockKey);
            } else {
                // Lock was already expired or taken by someone else
                LOGGER.fine("Lock already released or expired: " + lockKey);
                locked = false;
            }
        } catch (Exception e) {
            LOGGER.warning("Error releasing distributed lock: " + lockKey + " - " + e.getMessage());
        }
    }

    /**
     * Executes an action while holding the lock.
     *
     * @param action the action to execute
     * @return true if action completed successfully, false otherwise
     */
    public boolean withLock(Runnable action) {
        if (!tryLock()) {
            return false;
        }

        try {
            action.run();
            return true;
        } finally {
            unlock();
        }
    }

    /**
     * Closes the lock, releasing it if held.
     * <p>
     * This method is called automatically when used with try-with-resources.</p>
     */
    @Override
    public void close() {
        unlock();
    }

    /**
     * Checks if this lock is currently held.
     *
     * @return true if locked, false otherwise
     */
    public boolean isLocked() {
        return locked;
    }

    /**
     * Gets the lock key for debugging purposes.
     *
     * @return the lock key
     */
    public String getLockKey() {
        return lockKey;
    }
}
