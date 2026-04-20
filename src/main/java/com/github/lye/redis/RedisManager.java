package com.github.lye.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.lye.TradeFlow;
import com.github.lye.data.CentralBankStockManager;
import com.github.lye.data.Database;
import com.github.lye.data.Shop;
import com.github.lye.events.EconomicEventManager;
import com.github.lye.redis.messages.*;
import com.github.lye.pricing.model.ItemId;
import com.github.lye.pricing.model.PriceSnapshot;
import com.github.lye.pricing.service.PriceService;
import com.github.lye.config.settings.IPluginSettings;

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
        this.serverId = plugin.getServices().get(IPluginSettings.class).getRedisServerId();
        
        // Heartbeat publishing removed — ClusterSyncManager handles all heartbeat,
        // server discovery, and leader election via "tradeflow:cluster:heartbeat".
    }

    public void registerSubscriptions() {
        if (redisClient == null || !redisClient.isEnabled()) return;

        String channelPrices = plugin.getServices().get(IPluginSettings.class).getRedisChannelPrices();

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
                if (plugin.getServices().get(CentralBankStockManager.class) == null) return;
                StockUpdateMessage update = objectMapper.readValue(message, StockUpdateMessage.class);
                plugin.getServices().get(CentralBankStockManager.class).applyExternalSale(update.getItem(), update.getDelta());
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[Redis] Failed to process stock update.", e);
            }
        });

        // Heartbeat subscription removed — ClusterSyncManager handles all server
        // discovery via its own heartbeat system on "tradeflow:cluster:heartbeat".

        // Event update subscription — applies remote economic events
        redisClient.subscribe("tradeflow:event-updates", (channel, message) -> {
            try {
                EventUpdateMessage msg = objectMapper.readValue(message, EventUpdateMessage.class);
                // Deduplication: skip self-published messages
                if (this.serverId != null && this.serverId.equals(msg.getServerId())) {
                    return;
                }
                EconomicEventManager eventManager = plugin.getServices().get(EconomicEventManager.class);
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
            PriceSnapshot currentSnapshot = plugin.getServices().get(PriceService.class).getCurrentSnapshot();
            Map<ItemId, Double> newPrices = new HashMap<>();
            if (currentSnapshot != null) newPrices.putAll(currentSnapshot.getPrices());

            for (Map.Entry<String, Double> entry : prices.entrySet()) {
                String itemKey = entry.getKey();
                Double price = entry.getValue();

                Shop shop = plugin.getServices().get(Database.class).getShops().get(itemKey);
                if (shop != null) {
                    shop.setPrice(price);
                }
                newPrices.put(new ItemId(itemKey), price);
            }

            if (currentSnapshot != null) {
                plugin.getServices().get(PriceService.class).updatePriceSnapshot(new PriceSnapshot(newPrices, currentSnapshot.getBreakdowns()));
            }
        });
    }
}
