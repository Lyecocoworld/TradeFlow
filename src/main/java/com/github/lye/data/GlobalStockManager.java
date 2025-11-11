package com.github.lye.data;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import com.github.lye.TradeFlow;
import com.github.lye.database.GlobalStockData;
import com.github.lye.util.Format;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import com.github.lye.data.Database;
import java.util.HashMap;
import java.util.Map;
import com.github.lye.data.Shop;

public class GlobalStockManager {

    private final TradeFlow plugin;
    private final GlobalStockData globalStockData; // Can be null if DB is disabled

    // File-based fallback
    private File stockFile;
    private FileConfiguration stockConfig;

    private final Map<String, Integer> stockCounts = new HashMap<>();
    private final Map<String, Long> stockResetTimestamps = new HashMap<>();

    public GlobalStockManager(TradeFlow plugin, GlobalStockData globalStockData) {
        this.plugin = plugin;
        this.globalStockData = globalStockData;
        load();
    }

    public void load() {
        if (globalStockData != null) {
            globalStockData.loadAllStockData(stockCounts, stockResetTimestamps);
        } else {
            // Fallback to file
            this.stockFile = new File(plugin.getDataFolder(), "global_stock.yml");
            if (!stockFile.exists()) {
                plugin.saveResource("global_stock.yml", false);
            }
            this.stockConfig = YamlConfiguration.loadConfiguration(stockFile);
            for (String key : stockConfig.getKeys(false)) {
                stockCounts.put(key, stockConfig.getInt(key + ".count", 0));
                stockResetTimestamps.put(key, stockConfig.getLong(key + ".reset-timestamp", 0));
            }
        }
    }

    public void save() {
        // This method is now only for file-based fallback
        if (globalStockData != null || stockFile == null) {
            return;
        }
        for (Map.Entry<String, Integer> entry : stockCounts.entrySet()) {
            stockConfig.set(entry.getKey() + ".count", entry.getValue());
            stockConfig.set(entry.getKey() + ".reset-timestamp", stockResetTimestamps.get(entry.getKey()));
        }
        try {
            stockConfig.save(stockFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save global_stock.yml! " + e.getMessage());
        }
    }

    public int getRemainingStock(Shop shop) {
        if (shop.getGlobalStockLimit() <= 0) {
            return Integer.MAX_VALUE; // No limit
        }
        int currentCount = stockCounts.getOrDefault(shop.getName(), 0);
        return shop.getGlobalStockLimit() - currentCount;
    }

    public void recordSale(Shop shop, int amount) {
        if (shop.getGlobalStockLimit() <= 0) {
            return; // No limit, nothing to record
        }
        String itemName = shop.getName();
        int currentCount = stockCounts.getOrDefault(itemName, 0);
        int newCount = currentCount + amount;
        stockCounts.put(itemName, newCount);

        long timestamp = stockResetTimestamps.getOrDefault(itemName, 0L);
        if (timestamp == 0L) {
            timestamp = calculateNextReset(shop.getGlobalStockPeriod());
            stockResetTimestamps.put(itemName, timestamp);
        }

        if (globalStockData != null) {
            globalStockData.saveStock(itemName, newCount, timestamp);
        }
    }

    public void checkAndResetStocks() {
        long currentTime = System.currentTimeMillis();
        boolean needsFileSave = false;

        for (Shop shop : Database.get().getShops().values()) {
            if (shop.getGlobalStockLimit() > 0) {
                String itemName = shop.getName();
                long resetTime = stockResetTimestamps.getOrDefault(itemName, 0L);

                if (resetTime == 0L) { // First time this item is being handled
                    long nextReset = calculateNextReset(shop.getGlobalStockPeriod());
                    stockResetTimestamps.put(itemName, nextReset);
                    if (globalStockData != null) {
                        globalStockData.saveStock(itemName, stockCounts.getOrDefault(itemName, 0), nextReset);
                    } else {
                        needsFileSave = true;
                    }
                } else if (currentTime >= resetTime) {
                    plugin.getLogger().info("Global stock for '" + itemName + "' has been reset.");
                    long nextReset = calculateNextReset(shop.getGlobalStockPeriod());
                    stockCounts.put(itemName, 0);
                    stockResetTimestamps.put(itemName, nextReset);

                    if (globalStockData != null) {
                        globalStockData.saveStock(itemName, 0, nextReset);
                    } else {
                        needsFileSave = true;
                    }
                }
            }
        }

        if (needsFileSave) {
            save();
        }
    }

    private long calculateNextReset(String period) {
        Calendar calendar = Calendar.getInstance();
        switch (period.toLowerCase()) {
            case "daily":
                calendar.add(Calendar.DAY_OF_YEAR, 1);
                break;
            case "weekly":
                calendar.add(Calendar.WEEK_OF_YEAR, 1);
                break;
            case "monthly":
                calendar.add(Calendar.MONTH, 1);
                break;
            default:
                // Default to weekly if period is invalid
                calendar.add(Calendar.WEEK_OF_YEAR, 1);
                break;
        }
        return calendar.getTimeInMillis();
    }
}