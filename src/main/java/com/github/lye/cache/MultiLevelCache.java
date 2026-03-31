package com.github.lye.cache;

import com.github.lye.redis.RedisClient;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Multi-level cache implementation combining in-memory (L1) and Redis (L2).
 * <p>
 * Cache hierarchy:
 * <ul>
 *   <li>L1: In-memory cache with TTL (fastest, local only)</li>
 *   <li>L2: Redis cache (shared across servers)</li>
 *   <li>L3: Database/Source (slowest, authoritative)</li>
 * </ul>
 *
 * @param <K> key type
 * @param <V> value type
 * @author lye
 * @since 0.1
 */
public class MultiLevelCache<K, V> {

    private static final Logger LOGGER = Logger.getLogger(MultiLevelCache.class.getName());

    private final Map<K, CacheEntry<V>> localCache;
    private final RedisClient redisClient;
    private final String cachePrefix;
    private final Function<K, String> keySerializer;
    private final Function<String, V> valueDeserializer;
    private final Function<V, String> valueSerializer;
    private final long localTtlMillis;
    private final long redisTtlMillis;

    private MultiLevelCache(Builder<K, V> builder) {
        this.localCache = new ConcurrentHashMap<>();
        this.redisClient = builder.redisClient;
        this.cachePrefix = builder.cachePrefix;
        this.keySerializer = builder.keySerializer;
        this.valueDeserializer = builder.valueDeserializer;
        this.valueSerializer = builder.valueSerializer;
        this.localTtlMillis = builder.localTtlMillis;
        this.redisTtlMillis = builder.redisTtlMillis;
    }

    /**
     * Gets a value from cache, loading from source if necessary.
     *
     * @param key the cache key
     * @param loader the function to load the value if not cached
     * @return the cached value, or null if loader returns null
     */
    public V get(K key, Function<K, V> loader) {
        Objects.requireNonNull(key, "key");

        // L1: Check local cache (with TTL)
        CacheEntry<V> entry = localCache.get(key);
        if (entry != null && !entry.isExpired()) {
            return entry.value();
        }

        // L2: Check Redis cache
        String redisKey = cachePrefix + keySerializer.apply(key);
        String redisValue = redisClient.get(redisKey);
        if (redisValue != null) {
            V value = valueDeserializer.apply(redisValue);
            if (value != null) {
                // Populate L1
                localCache.put(key, new CacheEntry<>(value, System.currentTimeMillis() + localTtlMillis));
                return value;
            }
        }

        // L3: Load from source
        V value = loader.apply(key);
        if (value != null) {
            // Populate all levels
            localCache.put(key, new CacheEntry<>(value, System.currentTimeMillis() + localTtlMillis));
            redisClient.set(redisKey, valueSerializer.apply(value), redisTtlMillis);
        }

        return value;
    }

    /**
     * Gets a value from cache without loading from source.
     *
     * @param key the cache key
     * @return the cached value, or null if not found
     */
    public V getIfPresent(K key) {
        Objects.requireNonNull(key, "key");

        // L1: Check local cache (with TTL)
        CacheEntry<V> entry = localCache.get(key);
        if (entry != null) {
            if (entry.isExpired()) {
                localCache.remove(key);
                return null;
            }
            return entry.value();
        }

        // L2: Check Redis cache
        String redisKey = cachePrefix + keySerializer.apply(key);
        String redisValue = redisClient.get(redisKey);
        if (redisValue != null) {
            V value = valueDeserializer.apply(redisValue);
            if (value != null) {
                localCache.put(key, new CacheEntry<>(value, System.currentTimeMillis() + localTtlMillis));
            }
            return value;
        }

        return null;
    }

    /**
     * Puts a value into all cache levels.
     *
     * @param key the cache key
     * @param value the value to cache
     */
    public void put(K key, V value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");

        // L1: Update local cache
        localCache.put(key, new CacheEntry<>(value, System.currentTimeMillis() + localTtlMillis));

        // L2: Update Redis cache
        String redisKey = cachePrefix + keySerializer.apply(key);
        redisClient.set(redisKey, valueSerializer.apply(value), redisTtlMillis);
    }

    /**
     * Invalidates a value across all cache levels.
     *
     * @param key the cache key to invalidate
     */
    public void invalidate(K key) {
        Objects.requireNonNull(key, "key");

        // L1: Remove from local cache
        localCache.remove(key);

        // L2: Remove from Redis (expire immediately)
        String redisKey = cachePrefix + keySerializer.apply(key);
        redisClient.set(redisKey, "", 1);
    }

    /**
     * Invalidates all expired entries from local cache.
     */
    public void cleanUp() {
        long now = System.currentTimeMillis();
        localCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Invalidates all values from local cache.
     */
    public void invalidateAll() {
        localCache.clear();
    }

    /**
     * Gets the size of the local cache.
     *
     * @return the local cache size
     */
    public long size() {
        return localCache.size();
    }

    /**
     * Cache entry with TTL.
     *
     * @param <V> value type
     */
    private record CacheEntry<V>(V value, long expireTimeMillis) {
        boolean isExpired() {
            return System.currentTimeMillis() > expireTimeMillis;
        }
    }

    /**
     * Creates a new builder for MultiLevelCache.
     *
     * @param <K> key type
     * @param <V> value type
     * @return a new builder
     */
    public static <K, V> Builder<K, V> builder() {
        return new Builder<>();
    }

    /**
     * Builder for MultiLevelCache.
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
        private long localTtlMillis = 5000; // 5 seconds default
        private long redisTtlMillis = 60000; // 1 minute default

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

        public Builder<K, V> localTtlMillis(long localTtlMillis) {
            this.localTtlMillis = localTtlMillis;
            return this;
        }

        public Builder<K, V> redisTtlMillis(long redisTtlMillis) {
            this.redisTtlMillis = redisTtlMillis;
            return this;
        }

        public MultiLevelCache<K, V> build() {
            if (redisClient == null) {
                throw new IllegalStateException("redisClient is required");
            }
            if (valueDeserializer == null) {
                throw new IllegalStateException("valueDeserializer is required");
            }
            return new MultiLevelCache<>(this);
        }
    }
}
