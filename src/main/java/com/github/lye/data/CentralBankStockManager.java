package com.github.lye.data;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import com.github.lye.TradeFlow;
import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.config.settings.IPricingSettings;
import com.github.lye.repository.GlobalStockRepository;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages the virtual stock of the Central Bank.
 * Tracks the net flow of items (Sold to Bank - Bought from Bank) to drive dynamic pricing.
 */
public class CentralBankStockManager {

    private final TradeFlow plugin;
    private final GlobalStockRepository globalStockRepository; 
    private final Database database;
    private final IPluginSettings pluginSettings;
    private final IPricingSettings pricingSettings;

    private File stockFile;
    private FileConfiguration stockConfig;

    private final Map<String, Integer> stockLevels = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Double> activityScores = new java.util.concurrent.ConcurrentHashMap<>(); 
    private volatile double monetaryReserve = -1; // Internal cash reserve — volatile for cross-thread visibility

    public CentralBankStockManager(TradeFlow plugin, GlobalStockRepository globalStockRepository, Database database, IPluginSettings pluginSettings, IPricingSettings pricingSettings) {
        this.plugin = plugin;
        this.globalStockRepository = globalStockRepository;
        this.database = database;
        this.pluginSettings = pluginSettings;
        this.pricingSettings = pricingSettings;
        load();
    }

    public void load() {
        if (globalStockRepository != null) {
            Map<String, Long> dummyTimestamps = new HashMap<>();
            globalStockRepository.loadAllStockData(stockLevels, dummyTimestamps);
            
            // Monetary reserve loading for MySQL
            if (plugin.isMySqlEnabled() && plugin.getServerStateData() != null) {
                this.monetaryReserve = plugin.getServerStateData().getMonetaryReserve();
            }
        } else {
            this.stockFile = new File(plugin.getDataFolder(), "central_bank_stock.yml");
            if (!stockFile.exists()) {
                File oldFile = new File(plugin.getDataFolder(), "global_stock.yml");
                if (oldFile.exists()) {
                    oldFile.renameTo(stockFile);
                } else {
                    try { stockFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
                }
            }
            this.stockConfig = YamlConfiguration.loadConfiguration(stockFile);
            
            this.monetaryReserve = stockConfig.getDouble("system.monetary-reserve", -1);

            for (String key : stockConfig.getKeys(false)) {
                if (key.equals("system")) continue;
                if (stockConfig.contains(key + ".level")) {
                    stockLevels.put(key, stockConfig.getInt(key + ".level", 0));
                } else {
                    stockLevels.put(key, stockConfig.getInt(key + ".count", 0));
                }
            }
        }
        
        // Initial setup if reserve is -1 OR if it's 0 but we have shops (meaning it likely failed to init)
        if (this.monetaryReserve <= 0) {
            double calculated = calculateRequiredLiquidity();
            if (calculated > 0) {
                this.monetaryReserve = calculated;
                // plugin.getLogger().info("[CentralBank] Initialized internal monetary reserve: " + monetaryReserve);
                save();
            }
        }
    }

    public void save() {
        if (globalStockRepository != null) {
            for (Map.Entry<String, Integer> entry : stockLevels.entrySet()) {
                globalStockRepository.saveStock(entry.getKey(), entry.getValue(), 0L);
            }
            // Save monetary reserve to MySQL as well
            if (plugin.isMySqlEnabled() && plugin.getServerStateData() != null) {
                plugin.getServerStateData().saveMonetaryReserve(monetaryReserve);
            }
        }
        
        if (stockFile != null) {
            stockConfig.set("system.monetary-reserve", monetaryReserve);
            for (Map.Entry<String, Integer> entry : stockLevels.entrySet()) {
                stockConfig.set(entry.getKey() + ".level", entry.getValue());
            }
            try { stockConfig.save(stockFile); } catch (IOException e) { e.printStackTrace(); }
        }
    }

    public synchronized double getMonetaryReserve() {
        return monetaryReserve;
    }

    public synchronized void setMonetaryReserve(double amount) {
        this.monetaryReserve = amount;
        save();
    }

    public synchronized void addMoney(double amount) {
        this.monetaryReserve += amount;
        saveAsyncInternal();
    }

    public synchronized void removeMoney(double amount) {
        this.monetaryReserve -= amount;
        if (this.monetaryReserve < 0) this.monetaryReserve = 0;
        saveAsyncInternal();
    }

    private void saveAsyncInternal() {
        // Run DB/file save on the async scheduler — never block a region thread (fixes C6).
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> save());
    }

    private int getStockTargetDays() {
        // Accessing raw yaml from plugin config
        return plugin.getConfig().getInt("dynamic-pricing.stock-target-days", 7);
    }

    /**
     * Gets the current virtual stock for an item.
     * For a 500-player server, we initialize the bank with a target supply (default 7 days)
     * based on average daily player quotas.
     */
    public int getCurrentStock(Shop shop) {
        String itemName = shop.getName();
        if (stockLevels.containsKey(itemName)) {
            return stockLevels.get(itemName);
        }
        
        // Use the larger of the two quotas for a balanced startup reserve
        int dailyPlayerQuota = Math.max(shop.getMaxBuys(), shop.getMaxSells());
        if (dailyPlayerQuota <= 0) dailyPlayerQuota = pricingSettings.getDefaultDailyQuota(); 
        
        int population = pluginSettings.getTargetPopulation();
        if (population <= 0) population = pricingSettings.getDefaultPopulation();
        
        int days = getStockTargetDays();

        int calculatedInitial = (int) (dailyPlayerQuota * population * 0.05 * days);
        if (calculatedInitial <= 0) calculatedInitial = pricingSettings.getDefaultInitialStock(); 
        
        int initialStock = (shop.getGlobalStockLimit() > 0) ? shop.getGlobalStockLimit() : calculatedInitial;

        stockLevels.put(itemName, initialStock);
        return initialStock;
    }

    /**
     * Calculates the estimated capital needed to sustain the bank's buy-back 
     * for target days, accounting for circulation and specialization.
     */
    public double calculateRequiredLiquidity() {
        double total = 0;
        int population = pluginSettings.getTargetPopulation();
        if (population <= 0) population = pricingSettings.getDefaultPopulation();
        int days = getStockTargetDays();

        int shopCount = 0;
        for (Shop shop : database.getShops().values()) {
            double price = shop.getPrice();
            // Use same balanced quota as stock
            int dailyQuota = Math.max(shop.getMaxBuys(), shop.getMaxSells());
            if (dailyQuota <= 0) dailyQuota = pricingSettings.getDefaultDailyQuota();
            
            total += (price * dailyQuota * population * 0.05 * days);
            shopCount++;
        }
        return total;
    }

    /**
     * Gets the 'Ideal Stock' level for pricing math.
     * This ensures the price is 'Base Price' when stock is at its starting level.
     */
    public int getIdealStock(Shop shop) {
        // The ideal level is where we started
        String itemName = shop.getName();
        
        // If we have a physical limit, that's our ideal target
        if (shop.getGlobalStockLimit() > 0) {
            return shop.getGlobalStockLimit();
        }
        
        // Otherwise, use the calculated initial reserve as the ideal point
        int dailyPlayerQuota = shop.getMaxBuys();
        if (dailyPlayerQuota <= 0) dailyPlayerQuota = pricingSettings.getDefaultDailyQuota();
        int population = pluginSettings.getTargetPopulation();
        int days = getStockTargetDays();
        
        return (int) (dailyPlayerQuota * population * 0.05 * days);
    }

    /**
     * Checks if a Public Order (Commande Publique) is active for this item.
     * Active when stock is below 25% of the ideal level.
     */
    public boolean isPublicOrderActive(Shop shop) {
        return getCurrentStock(shop) < (getIdealStock(shop) * 0.25);
    }

    /**
     * Gets the price bonus for a Public Order.
     */
    public double getPublicOrderBonus() {
        return pricingSettings.getPublicOrderBonus();
    }

    public enum EconomicPolicy { 
        EXPANSION("<green>EXPANSION</green>", 0.8), 
        STABLE("<white>STABLE</white>", 1.0),      
        AUSTERITY("<red>AUSTÉRITÉ</red>", 1.5);    
        
        private final String display;
        private final double taxMultiplier;
        EconomicPolicy(String d, double m) { this.display = d; this.taxMultiplier = m; }
        public String getDisplay() { return display; }
        public double getTaxMultiplier() { return taxMultiplier; }
    }

    private EconomicPolicy currentPolicy = EconomicPolicy.STABLE;

    public void updatePolicy() {
        String bankName = pluginSettings.getCentralBankAccount();
        if (bankName == null || bankName.isEmpty()) return;

        double balance = com.github.lye.util.EconomyUtil.getCentralBankBalance(plugin);
        double required = calculateRequiredLiquidity();

        if (balance > required * pricingSettings.getExpansionThreshold()) currentPolicy = EconomicPolicy.EXPANSION;
        else if (balance < required * pricingSettings.getAusterityThreshold()) currentPolicy = EconomicPolicy.AUSTERITY;
        else currentPolicy = EconomicPolicy.STABLE;
    }

    public EconomicPolicy getCurrentPolicy() { return currentPolicy; }

    public double getDynamicSpread(String itemName) {
        double activity = activityScores.getOrDefault(itemName, 0.0);
        double baseSpread = Math.min(0.5, activity / 5000.0); 
        return Math.min(0.8, baseSpread * currentPolicy.getTaxMultiplier());
    }

    public void recordSale(Shop shop, int amount) {
        String itemName = shop.getName();
        int current = getCurrentStock(shop);
        int newStock = current + amount;
        updateActivity(itemName, amount);
        stockLevels.put(itemName, newStock);
        
        if (globalStockRepository != null) {
            saveAsync(itemName, newStock);
        } else {
            save(); // Force immediate save in YML mode to prevent data loss on reload
        }
    }

    public void recordBuy(Shop shop, int amount) {
        String itemName = shop.getName();
        int current = getCurrentStock(shop);
        int newStock = current - amount;
        updateActivity(itemName, amount);
        if (newStock < 0) newStock = 0; 
        stockLevels.put(itemName, newStock);
        
        if (globalStockRepository != null) {
            saveAsync(itemName, newStock);
        } else {
            save(); // Force immediate save in YML mode
        }
    }

    private void updateActivity(String itemName, int amount) {
        double currentActivity = activityScores.getOrDefault(itemName, 0.0);
        double alpha = pricingSettings.getActivityAlpha();
        activityScores.put(itemName, (currentActivity * (1.0 - alpha)) + (amount * alpha));
    }
    
    public void applyExternalSale(String itemName, int amount) {
        int current = stockLevels.getOrDefault(itemName, 0);
        stockLevels.put(itemName, current + amount);
    }

    /**
     * Sets the stock level for an item directly (used for GMQ weekly restock sync).
     *
     * @param itemName the item identifier
     * @param stock    the new stock level
     */
    public void setStock(String itemName, int stock) {
        if (stock < 0) stock = 0;
        stockLevels.put(itemName, stock);
        saveAsync(itemName, stock);
    }

    private void saveAsync(String itemName, int quantity) {
        if (globalStockRepository != null) {
             // Run JDBC on the async scheduler — never block a region thread (fixes C6).
             plugin.getServer().getAsyncScheduler().runNow(plugin, task ->
                 globalStockRepository.saveStock(itemName, quantity, 0L));
        }
    }
}