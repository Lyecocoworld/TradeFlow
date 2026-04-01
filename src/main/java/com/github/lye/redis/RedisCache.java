package com.github.lye.redis;

/**
 * Minimal cache adapter for Redis key/value operations.
 * Values are treated as opaque strings (typically JSON).
 */
public interface RedisCache {

    void set(String key, String value, long ttlMillis);

    String get(String key);
}

