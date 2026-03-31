package com.github.lye.redis;

/**
 * Simple listener interface for Redis pub/sub messages.
 */
@FunctionalInterface
public interface RedisMessageListener {
    void onMessage(String channel, String message);
}

