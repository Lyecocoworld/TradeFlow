package com.github.lye.market;

import com.github.lye.TradeFlow;
import com.github.lye.data.CentralBankStockManager;
import com.github.lye.data.Database;
import com.github.lye.data.Shop;
import com.github.lye.repository.ServerStateRepository;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class StockManager {

    private final TradeFlow plugin;
    private final ServerStateRepository serverState;
    private final com.github.lye.data.CentralBankStockManager centralBankManager;
    private final Random random = new Random();
    private final AtomicBoolean resetting = new AtomicBoolean(false);
    private volatile long lastResetMemory = 0L;

    private static final String KEY_LAST_STOCK_RESET = "last_stock_reset";
    private static final long WEEKLY_MS = 7L * 24L * 60L * 60L * 1000L;

    public StockManager(TradeFlow plugin, ServerStateRepository serverState) {
        this.plugin = plugin;
        this.serverState = serverState;
        this.centralBankManager = plugin.getServices().get(CentralBankStockManager.class);
    }

    public void checkWeeklyReset() {
        if (!resetting.compareAndSet(false, true)) {
            return;
        }

        long now = System.currentTimeMillis();
        long fallback = lastResetMemory;

        if (serverState != null) {
            serverState.getState(KEY_LAST_STOCK_RESET)
                    .thenAccept(val -> {
                        long lastReset = fallback;
                        if (val != null) {
                            try {
                                lastReset = Long.parseLong(val);
                            } catch (NumberFormatException e) {
                                plugin.getLogger().log(Level.WARNING,
                                        "[StockManager] Invalid last reset timestamp: " + val);
                            }
                        }

                        if (now - lastReset >= WEEKLY_MS) {
                            performRestockAsync(now);
                        } else {
                            resetting.set(false);
                        }
                    })
                    .exceptionally(ex -> {
                        plugin.getLogger().log(Level.SEVERE,
                                "[StockManager] Failed to read reset timestamp", ex);
                        resetting.set(false);
                        return null;
                    });
        } else {
            if (now - fallback >= WEEKLY_MS) {
                performRestockAsync(now);
            } else {
                resetting.set(false);
            }
        }
    }

    private void performRestockAsync(long now) {
        CompletableFuture.runAsync(() -> performRestock())
                .thenRun(() -> {
                    if (serverState != null) {
                        serverState.setState(KEY_LAST_STOCK_RESET, String.valueOf(now))
                                .thenRun(() -> resetting.set(false))
                                .exceptionally(ex -> {
                                    plugin.getLogger().log(Level.SEVERE,
                                            "[StockManager] Failed to persist reset timestamp", ex);
                                    resetting.set(false);
                                    return null;
                                });
                    } else {
                        lastResetMemory = now;
                        resetting.set(false);
                    }
                })
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE,
                            "[StockManager] Restock failed", ex);
                    resetting.set(false);
                    return null;
                });
    }

    public void performRestock() {
        Map<String, Shop> shops = plugin.getServices().get(Database.class).getShops();
        int resetCount = 0;
        double totalCost = 0.0;

        for (Shop shop : shops.values()) {
            int min = shop.getMinBaseStock();
            int max = shop.getMaxBaseStock();

            if (min > 0 && max >= min) {
                int targetStock = random.nextInt((max - min) + 1) + min;

                int current = centralBankManager.getCurrentStock(shop);

                if (current < targetStock) {
                    int added = targetStock - current;

                    double cost = added * shop.getBasePrice();

                    if (centralBankManager.getMonetaryReserve() > 0) {
                        centralBankManager.applyExternalSale(shop.getName(), added);
                        centralBankManager.removeMoney(cost);
                        totalCost += cost;

                        shop.setCurrentStock(targetStock);
                        plugin.getServices().get(Database.class).putShop(shop.getName(), shop);

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
