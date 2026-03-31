package com.github.lye.cache;

import com.github.lye.data.Shop;
import com.github.lye.redis.RedisClient;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.function.Function;

/**
 * Cache manager for Shop objects.
 * <p>
 * Uses multi-level caching (In-memory L1 + Redis L2) to reduce database load
 * and improve cross-server synchronization.</p>
 *
 * @author lye
 * @since 0.1
 */
public class ShopCache {

    private static final String CACHE_PREFIX = "tf:shop:";
    private static final long LOCAL_TTL_MS = 5000;  // 5 seconds
    private static final long REDIS_TTL_MS = 60000; // 1 minute

    private final MultiLevelCache<String, Shop> cache;
    private final Gson gson;
    private final Type shopType;

    public ShopCache(RedisClient redisClient, Gson gson) {
        this.gson = gson;
        this.shopType = new TypeToken<Shop>() {}.getType();

        this.cache = MultiLevelCache.<String, Shop>builder()
                .redisClient(redisClient)
                .cachePrefix(CACHE_PREFIX)
                .valueSerializer(shop -> gson.toJson(shop))
                .valueDeserializer(json -> gson.fromJson(json, shopType))
                .localTtlMillis(LOCAL_TTL_MS)
                .redisTtlMillis(REDIS_TTL_MS)
                .build();
    }

    /**
     * Gets a shop from cache, loading from database if necessary.
     *
     * @param shopKey the shop key
     * @param loader the function to load the shop from database
     * @return the shop, or null if not found
     */
    public Shop get(String shopKey, Function<String, Shop> loader) {
        return cache.get(shopKey, loader);
    }

    /**
     * Gets a shop from cache without loading from database.
     *
     * @param shopKey the shop key
     * @return the cached shop, or null if not found
     */
    public Shop getIfPresent(String shopKey) {
        return cache.getIfPresent(shopKey);
    }

    /**
     * Puts a shop into cache.
     *
     * @param shopKey the shop key
     * @param shop the shop to cache
     */
    public void put(String shopKey, Shop shop) {
        cache.put(shopKey, shop);
    }

    /**
     * Invalidates a shop from cache.
     *
     * @param shopKey the shop key to invalidate
     */
    public void invalidate(String shopKey) {
        cache.invalidate(shopKey);
    }

    /**
     * Invalidates all shops from cache.
     */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    /**
     * Gets the approximate cache size.
     *
     * @return the number of shops in local cache
     */
    public long size() {
        return cache.size();
    }
}
