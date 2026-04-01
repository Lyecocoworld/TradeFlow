package com.github.lye.redis;

/**
 * Fallback Redis client that does nothing.
 * Used when Redis is not configured or unavailable.
 */
public final class NoOpRedisClient implements RedisClient {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void set(String key, String value, long ttlMillis) {
        // no-op
    }

    @Override
    public String get(String key) {
        return null;
    }

    @Override
    public void publish(String channel, String message) {
        // no-op
    }

    @Override
    public void subscribe(String channel, RedisMessageListener listener) {
        // no-op
    }

    @Override
    public boolean setNxEx(String key, String value, long ttlSeconds) {
        return false; // no-op, lock not acquired
    }

    @Override
    public boolean getDel(String key, String expectedValue) {
        return false; // no-op
    }

    @Override
    public void close() {
        // no-op
    }
}

