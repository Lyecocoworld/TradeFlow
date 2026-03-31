package com.github.lye.redis;

/**
 * High-level Redis client abstraction combining cache and pub/sub.
 *
 * Implementations are responsible for connection management
 * and must be safe to use from Bukkit/Folia contexts.
 */
public interface RedisClient extends RedisCache, RedisPubSub, AutoCloseable {

    boolean isEnabled();

    /**
     * Atomic SET NX EX operation for distributed locking.
     * Only sets the key if it doesn't exist (NX), with an expiration time (EX).
     *
     * @param key the lock key
     * @param value the lock value (typically a unique identifier)
     * @param ttlSeconds time-to-live in seconds
     * @return true if the key was set (lock acquired), false otherwise
     */
    boolean setNxEx(String key, String value, long ttlSeconds);

    /**
     * Atomic GET and DELETE operation for safe distributed unlocking.
     * Only deletes the key if the value matches (prevents accidental unlock by others).
     *
     * @param key the lock key
     * @param expectedValue the value we expect (our lock identifier)
     * @return true if the key was deleted, false otherwise
     */
    boolean getDel(String key, String expectedValue);

    @Override
    void close();
}

