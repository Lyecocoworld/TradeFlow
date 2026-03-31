package com.github.lye.market;

import com.github.lye.TradeFlow;
import com.github.lye.data.Shop;
import com.github.lye.repository.ServerStateRepository;

import java.util.Map;
import java.util.Random;
import java.util.logging.Level;

public class StockManager {

    private final TradeFlow plugin;
    private final ServerStateRepository serverState;
    private final com.github.lye.data.CentralBankStockManager centralBankManager;
    private final Random random = new Random();
    private long lastResetMemory = 0L; // Fallback when repository is unavailable

    private static final String KEY_LAST_STOCK_RESET = "last_stock_reset";
    private static final long WEEKLY_MS = 7L * 24L * 60L * 60L * 1000L;

    public StockManager(TradeFlow plugin, ServerStateRepository serverState) {
        this.plugin = plugin;
        this.serverState = serverState;
        this.centralBankManager = plugin.getCentralBankStockManager();
    }

    public void checkWeeklyReset() {
        long now = System.currentTimeMillis();
        long lastReset = lastResetMemory;

        if (serverState != null) {
            String val = serverState.getState(KEY_LAST_STOCK_RESET);
            if (val != null) {
                try {
                    lastReset = Long.parseLong(val);
                } catch (NumberFormatException e) {
                    plugin.getLogger().log(Level.WARNING, "[StockManager] Invalid last reset timestamp: " + val);
                }
            }
        }

        if (now - lastReset >= WEEKLY_MS) {
            plugin.getLogger().info("[StockManager] Performing weekly stock reset...");
            performRestock();
            if (serverState != null) {
                serverState.setState(KEY_LAST_STOCK_RESET, String.valueOf(now));
            } else {
                // Keep at least an in-memory marker to avoid spamming resets when persistence is unavailable
                lastResetMemory = now;
            }
        }
    }

    public void performRestock() {
        Map<String, Shop> shops = plugin.getLoadedShops();
        int resetCount = 0;
        double totalCost = 0.0;

        for (Shop shop : shops.values()) {
            int min = shop.getMinBaseStock();
            int max = shop.getMaxBaseStock();

            if (min > 0 && max >= min) {
                // Generate a target random stock for the new week
                int targetStock = random.nextInt((max - min) + 1) + min;
                
                // Get current stock from the AUTHORITATIVE source
                int current = centralBankManager.getCurrentStock(shop);
                
                // Top-up logic: only add if current is below target
                // If current stock is huge (oversupply), we don't reduce it (players keep their surplus)
                if (current < targetStock) {
                    int added = targetStock - current;
                    
                    // Economic Cost Calculation
                    // The Central Bank must PAY to produce these items.
                    // Cost = Quantity * Base Price (Production Cost)
                    double cost = added * shop.getBasePrice();
                    
                    if (centralBankManager.getMonetaryReserve() > 0) {
                        // Apply updates
                        centralBankManager.applyExternalSale(shop.getName(), added); // Adds stock
                        centralBankManager.removeMoney(cost);
                        totalCost += cost;
                        
                        // Sync Shop object for UI/Persistence
                        shop.setCurrentStock(targetStock);
                        plugin.getDatabase().putShop(shop.getName(), shop);
                        
                        resetCount++;
                    } else {
                        plugin.getLogger().warning("[StockManager] Central Bank is bankrupt! Cannot restock " + shop.getName());
                    }
                }
            }
        }
        
        if (resetCount > 0) {
            plugin.getLogger().info("[StockManager] Restocked " + resetCount + " items.");
            plugin.getLogger().info("[CentralBank] Production cost: " + com.github.lye.util.Format.currency(totalCost));
        }
    }
}
