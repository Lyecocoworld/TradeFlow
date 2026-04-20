package com.github.lye.pricing;

import com.github.lye.TradeFlow;
import com.github.lye.cache.CaffeineCache;
import com.github.lye.data.Database;
import com.github.lye.data.Shop;
import com.github.lye.redis.DistributedLock;
import com.github.lye.redis.RedisClient;
import com.github.lye.redis.messages.BinaryMessage;
import com.github.lye.util.GsonShared;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Manages price updates using delta sync to reduce Redis traffic.
 * <p>
 * Instead of sending full price snapshots, only sends changed prices.
 * Uses version numbers to detect conflicts and ensure consistency.
 * Uses binary serialization for efficient network transfer.</p>
 *
 * @author lye
 * @since 0.1
 */
public class DeltaPriceManager {

    private static final Logger LOGGER = Logger.getLogger(DeltaPriceManager.class.getName());
    private static final String PRICE_VERSION_PREFIX = "tf:price:version:";
    private static final String DELTA_CHANNEL = "tradeflow:price:delta";

    private final TradeFlow plugin;
    private final RedisClient redisClient;
    private final Map<String, AtomicLong> localVersions;
    private final CaffeineCache<String, PriceEntry> priceCache;
    private final String serverId;

    public DeltaPriceManager(TradeFlow plugin, RedisClient redisClient, java.util.concurrent.Executor asyncExecutor, String serverId) {
        this.plugin = plugin;
        this.redisClient = redisClient;
        this.localVersions = new ConcurrentHashMap<>();
        this.serverId = serverId != null ? serverId : "unknown";

        // Initialize price cache with Caffeine
        this.priceCache = CaffeineCache.<String, PriceEntry>builder()
                .redisClient(redisClient)
                .cachePrefix("tf:price:")
                .valueSerializer(entry -> GsonShared.INSTANCE.toJson(entry))
                .valueDeserializer(json -> GsonShared.INSTANCE.fromJson(json, PriceEntry.class))
                .localTtlMillis(10000) // 10 seconds
                .redisTtlMillis(60000) // 1 minute
                .maximumSize(500)
                .asyncExecutor(asyncExecutor)
                .recordStats(true)
                .build();
    }

    /**
     * Creates a DeltaPriceManager with default server ID.
     */
    public DeltaPriceManager(TradeFlow plugin, RedisClient redisClient, java.util.concurrent.Executor asyncExecutor) {
        this(plugin, redisClient, asyncExecutor, "unknown");
    }

    /**
     * Gets a price entry from cache.
     *
     * @param itemKey the item key
     * @return the price entry, or null if not found
     */
    public PriceEntry getPrice(String itemKey) {
        return priceCache.getIfPresent(itemKey);
    }

    /**
     * Gets a price entry asynchronously.
     *
     * @param itemKey the item key
     * @param loader the loader to fetch from database if not cached
     * @return a CompletableFuture with the price entry
     */
    public CompletableFuture<PriceEntry> getPriceAsync(String itemKey, java.util.function.Function<String, Shop> loader) {
        return priceCache.getAsync(itemKey, key -> {
            Shop shop = loader.apply(key);
            if (shop != null) {
                return new PriceEntry(
                    shop.getName(),
                    shop.getPrice(),
                    getCurrentVersion(itemKey),
                    System.currentTimeMillis()
                );
            }
            return null;
        });
    }

    /**
     * Updates a price and publishes only the delta to Redis using binary serialization.
     *
     * @param itemKey the item key
     * @param oldPrice the old price
     * @param newPrice the new price
     */
    public void updatePrice(String itemKey, double oldPrice, double newPrice) {
        long newVersion = incrementVersion(itemKey);

        PriceEntry entry = new PriceEntry(itemKey, newPrice, newVersion, System.currentTimeMillis());
        priceCache.put(itemKey, entry);

        // Publish delta to Redis using binary format (60% smaller than pipe-delimited string)
        BinaryMessage.PriceUpdateMessage message = new BinaryMessage.PriceUpdateMessage(
                serverId, itemKey, newPrice, newVersion
        );
        byte[] binaryData = message.serialize();

        // Publish as base64 to stay compatible with Redis pub/sub string channel
        String encoded = java.util.Base64.getEncoder().encodeToString(binaryData);
        redisClient.publish(DELTA_CHANNEL, encoded);

        LOGGER.fine("Published binary price delta: " + itemKey + " " + oldPrice + " -> " + newPrice + " v" + newVersion);
    }

    /**
     * Applies a price delta from another server.
     * <p>
     * Supports both legacy pipe-delimited format and binary format for backward compatibility.</p>
     *
     * @param data the delta data (base64-encoded binary or pipe-delimited string)
     */
    public void applyDelta(String data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        // Check format: binary (base64) or legacy pipe-delimited
        if (data.contains("|")) {
            applyLegacyDelta(data);
        } else {
            applyBinaryDelta(data);
        }
    }

    /**
     * Applies binary price delta (new format).
     */
    private void applyBinaryDelta(String base64Data) {
        try {
            byte[] binaryData = java.util.Base64.getDecoder().decode(base64Data);
            BinaryMessage message = BinaryMessage.deserialize(binaryData);

            if (message.getType() != BinaryMessage.MessageType.PRICE_UPDATE) {
                LOGGER.warning("Unexpected message type in binary delta: " + message.getType());
                return;
            }

            BinaryMessage.PriceUpdateMessage priceMsg = (BinaryMessage.PriceUpdateMessage) message;
            String itemKey = priceMsg.getItemKey();
            double newPrice = priceMsg.getNewPrice();
            long version = priceMsg.getVersion();

            applyPriceUpdate(itemKey, newPrice, version, "binary");
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Failed to decode binary price delta: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.warning("Error applying binary price delta: " + e.getMessage());
        }
    }

    /**
     * Applies legacy pipe-delimited price delta (for backward compatibility).
     */
    private void applyLegacyDelta(String delta) {
        String[] parts = delta.split("\\|");
        if (parts.length != 4) {
            LOGGER.warning("Invalid price delta format: " + delta);
            return;
        }

        try {
            String itemKey = parts[0];
            double oldPrice = Double.parseDouble(parts[1]);
            double newPrice = Double.parseDouble(parts[2]);
            long version = Long.parseLong(parts[3]);

            applyPriceUpdate(itemKey, newPrice, version, "legacy");
        } catch (NumberFormatException e) {
            LOGGER.warning("Failed to parse price delta: " + delta);
        }
    }

    /**
     * Common price update application logic with distributed locking.
     * <p>
     * Uses distributed lock to prevent race conditions when multiple servers
     * update the same price simultaneously.</p>
     */
    private void applyPriceUpdate(String itemKey, double newPrice, long version, String format) {
        String lockKey = "tradeflow:price:update:" + itemKey;
        DistributedLock lock = new DistributedLock(redisClient, lockKey, 5000);
        lock.tryLockAsync(1000, plugin).thenAccept(acquired -> {
            try {
                if (!acquired) {
                    LOGGER.warning("Could not acquire lock for price update: " + itemKey + " - another server is updating");
                    return;
                }

                long currentVersion = getCurrentVersion(itemKey);
                if (version <= currentVersion) {
                    LOGGER.fine("Ignoring stale price update: " + itemKey + " v" + version + " <= v" + currentVersion);
                    return;
                }

                Map<String, Shop> shops = plugin.getServices().get(Database.class).getShops();
                Shop shop = shops.get(itemKey);
                if (shop != null) {
                    shop.setPrice(newPrice);
                    localVersions.put(itemKey, new AtomicLong(version));
                    priceCache.put(itemKey, new PriceEntry(itemKey, newPrice, version, System.currentTimeMillis()));
                    LOGGER.info("Applied price delta from remote (" + format + "): " + itemKey + " = " + newPrice + " v" + version);
                }
            } finally {
                lock.unlock();
            }
        }).exceptionally(ex -> {
            LOGGER.warning("Error applying price update for " + itemKey + ": " + ex.getMessage());
            lock.unlock();
            return null;
        });
    }

    /**
     * Gets the current version for an item.
     *
     * @param itemKey the item key
     * @return the current version
     */
    public long getCurrentVersion(String itemKey) {
        return localVersions.computeIfAbsent(itemKey, k -> new AtomicLong(0)).get();
    }

    /**
     * Increments the version for an item.
     *
     * @param itemKey the item key
     * @return the new version
     */
    private long incrementVersion(String itemKey) {
        return localVersions.computeIfAbsent(itemKey, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Resets all prices to initial state.
     */
    public void reset() {
        localVersions.clear();
        priceCache.invalidateAll();
        LOGGER.info("Price delta manager reset");
    }

    /**
     * Gets cache statistics.
     *
     * @return the cache statistics
     */
    public String getStats() {
        var stats = priceCache.getStats();
        if (stats == null) {
            return "CaffeineCache[stats=disabled]";
        }
        return String.format("CaffeineCache[hits=%d, misses=%d, hitRate=%.2f%%, size=%d]",
                stats.hitCount(),
                stats.missCount(),
                stats.hitRate() * 100,
                priceCache.size()
        );
    }

    /**
     * Price entry with version information.
     */
    public static class PriceEntry {
        private final String itemKey;
        private final double price;
        private final long version;
        private final long timestamp;

        public PriceEntry(String itemKey, double price, long version, long timestamp) {
            this.itemKey = itemKey;
            this.price = price;
            this.version = version;
            this.timestamp = timestamp;
        }

        public String getItemKey() { return itemKey; }
        public double getPrice() { return price; }
        public long getVersion() { return version; }
        public long getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return String.format("%s=%.2f v%d", itemKey, price, version);
        }
    }
}
