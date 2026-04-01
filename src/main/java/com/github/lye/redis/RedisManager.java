package com.github.lye.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.lye.TradeFlow;
import com.github.lye.data.Shop;
import com.github.lye.redis.messages.*;
import com.github.lye.pricing.model.ItemId;
import com.github.lye.pricing.model.PriceSnapshot;
import org.bukkit.Bukkit;
import com.github.lye.events.EconomicEventManager;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class RedisManager {

    private final TradeFlow plugin;
    private final RedisClient redisClient;
    private final ObjectMapper objectMapper;
    private final String serverId;

    public RedisManager(TradeFlow plugin, RedisClient redisClient) {
        this.plugin = plugin;
        this.redisClient = redisClient;
        this.objectMapper = new ObjectMapper();
        this.serverId = plugin.getPluginSettings().getRedisServerId();
        
        if (redisClient != null && redisClient.isEnabled()) {
            startHeartbeat();
        }
    }

    public void registerSubscriptions() {
        if (redisClient == null || !redisClient.isEnabled()) return;

        String channelPrices = plugin.getPluginSettings().getRedisChannelPrices();
        String channelGlobal = plugin.getPluginSettings().getRedisChannelGlobal();
        String channelHeartbeat = plugin.getPluginSettings().getRedisChannelHeartbeat();

        redisClient.subscribe(channelPrices, (channel, message) -> {
            try {
                BulkPriceUpdateMessage update = objectMapper.readValue(message, BulkPriceUpdateMessage.class);
                applyBulkPriceUpdate(update.getPrices());
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[Redis] Failed to process bulk price update.", e);
            }
        });

        redisClient.subscribe("tradeflow:stock-updates", (channel, message) -> {
            try {
                if (plugin.getCentralBankStockManager() == null) return;
                StockUpdateMessage update = objectMapper.readValue(message, StockUpdateMessage.class);
                plugin.getCentralBankStockManager().applyExternalSale(update.getItem(), update.getDelta());
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[Redis] Failed to process stock update.", e);
            }
        });

        redisClient.subscribe(channelHeartbeat, (channel, message) -> {
            try {
                HeartbeatMessage msg = objectMapper.readValue(message, HeartbeatMessage.class);
                if (!msg.getServerId().equals(this.serverId)) {
                    plugin.getLogger().fine("[Redis] Remote Server Heartbeat: " + msg.getServerId() + " (TPS: " + msg.getTps() + ")");
                }
            } catch (Exception ignored) {}
        });

        // Event update subscription — applies remote economic events
        redisClient.subscribe("tradeflow:event-updates", (channel, message) -> {
            try {
                EventUpdateMessage msg = objectMapper.readValue(message, EventUpdateMessage.class);
                // Deduplication: skip self-published messages
                if (this.serverId != null && this.serverId.equals(msg.getServerId())) {
                    return;
                }
                EconomicEventManager eventManager = plugin.getEconomicEventManager();
                if (eventManager != null) {
                    eventManager.applyRemoteEventUpdate(msg);
                }
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "[Redis] Failed to process event update.", e);
            }
        });
    }

    private void applyBulkPriceUpdate(Map<String, Double> prices) {
        if (prices == null || prices.isEmpty()) return;

        com.github.lye.util.FoliaSchedulers.runGlobal(plugin, () -> {
            PriceSnapshot currentSnapshot = plugin.getPriceService().getCurrentSnapshot();
            Map<ItemId, Double> newPrices = new HashMap<>();
            if (currentSnapshot != null) newPrices.putAll(currentSnapshot.getPrices());

            for (Map.Entry<String, Double> entry : prices.entrySet()) {
                String itemKey = entry.getKey();
                Double price = entry.getValue();

                Shop shop = plugin.getLoadedShops().get(itemKey);
                if (shop != null) {
                    shop.setPrice(price);
                    plugin.getLoadedShops().put(itemKey, shop);
                }
                newPrices.put(new ItemId(itemKey), price);
            }

            if (currentSnapshot != null) {
                plugin.getPriceService().updatePriceSnapshot(new PriceSnapshot(newPrices, currentSnapshot.getBreakdowns()));
            }
        });
    }

    private void startHeartbeat() {
        plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, task -> {
            try {
                // Capture player count on region thread before doing IO
                final int onlineCount = Bukkit.getOnlinePlayers().size();
                HeartbeatMessage msg = new HeartbeatMessage(
                    serverId,
                    System.currentTimeMillis(),
                    20.0,
                    onlineCount
                );
                String payload = objectMapper.writeValueAsString(msg);
                redisClient.publish(plugin.getPluginSettings().getRedisChannelHeartbeat(), payload);
            } catch (Exception e) {
                plugin.getLogger().warning("[Redis] Heartbeat failed: " + e.getMessage());
            }
        }, 5000L, 5000L, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
}
