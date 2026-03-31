package com.github.lye.redis;

/**
 * Minimal pub/sub adapter for Redis.
 */
public interface RedisPubSub {

    void publish(String channel, String message);

    void subscribe(String channel, RedisMessageListener listener);
}

