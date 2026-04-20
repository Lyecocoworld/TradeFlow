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
import java.util.concurrent.atomic.AtomicReference;

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
    private final AtomicReference<Double> monetaryReserve = new AtomicReference<>(-1.0); // Internal cash reserve — thread-safe

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
            if (plugin.getBootstrap().getDatabaseBootstrap().isMySqlEnabled() && plugin.getBootstrap().getDatabaseBootstrap().getServerStateData() != null) {
                this.monetaryReserve.set(plugin.getBootstrap().getDatabaseBootstrap().getServerStateData().getMonetaryReserve().join());
            }
        } else {
            this.stockFile = new File(plugin.getDataFolder(), "central_bank_stock.yml");
            if (!stockFile.exists()) {
                File oldFile = new File(plugin.getDataFolder(), "global_stock.yml");
                if (oldFile.exists()) {
                    oldFile.renameTo(stockFile);
                } else {
                    try { stockFile.createNewFile(); } catch (IOException e) { plugin.getLogger().severe("[CentralBank] Could not create stock file: " + e.getMessage()); }
                }
            }
            this.stockConfig = YamlConfiguration.loadConfiguration(stockFile);
            
            this.monetaryReserve.set(stockConfig.getDouble("system.monetary-reserve", -1));

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
        if (this.monetaryReserve.get() <= 0) {
            double calculated = calculateRequiredLiquidity();
            if (calculated > 0) {
                this.monetaryReserve.set(calculated);
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
            if (plugin.getBootstrap().getDatabaseBootstrap().isMySqlEnabled() && plugin.getBootstrap().getDatabaseBootstrap().getServerStateData() != null) {
                plugin.getBootstrap().getDatabaseBootstrap().getServerStateData().saveMonetaryReserve(monetaryReserve.get());
            }
        }
        
        if (stockFile != null) {
            stockConfig.set("system.monetary-reserve", monetaryReserve.get());
            for (Map.Entry<String, Integer> entry : stockLevels.entrySet()) {
                stockConfig.set(entry.getKey() + ".level", entry.getValue());
            }
            try { stockConfig.save(stockFile); } catch (IOException e) { plugin.getLogger().severe("[CentralBank] Could not save stock data: " + e.getMessage()); }
        }
    }

    public double getMonetaryReserve() {
        return monetaryReserve.get();
    }

    public void setMonetaryReserve(double amount) {
        this.monetaryReserve.set(amount);
        saveAsyncInternal();
    }

    public void addMoney(double amount) {
        monetaryReserve.updateAndGet(current -> current + amount);
        saveAsyncInternal();
    }

    public void removeMoney(double amount) {
        monetaryReserve.updateAndGet(current -> Math.max(0, current - amount));
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

        // Use computeIfAbsent for atomic lazy-initialisation (prevents race where two
        // threads both see "absent" and each inserts a different initial value).
        return stockLevels.computeIfAbsent(itemName, key -> {
            // Use the larger of the two quotas for a balanced startup reserve
            int dailyPlayerQuota = Math.max(shop.getMaxBuys(), shop.getMaxSells());
            if (dailyPlayerQuota <= 0) dailyPlayerQuota = pricingSettings.getDefaultDailyQuota();

            int population = pluginSettings.getTargetPopulation();
            if (population <= 0) population = pricingSettings.getDefaultPopulation();

            int days = getStockTargetDays();

            int calculatedInitial = (int) (dailyPlayerQuota * population * 0.05 * days);
            if (calculatedInitial <= 0) calculatedInitial = pricingSettings.getDefaultInitialStock();

            return (shop.getGlobalStockLimit() > 0) ? shop.getGlobalStockLimit() : calculatedInitial;
        });
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
     * Active when stock is below the configured threshold ratio of the ideal level.
     */
    public boolean isPublicOrderActive(Shop shop) {
        return getCurrentStock(shop) < (getIdealStock(shop) * pricingSettings.getPublicOrderThreshold());
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
        double baseSpread = Math.min(pricingSettings.getDynamicSpreadMaxBase(), activity / pricingSettings.getDynamicSpreadActivityDivisor());
        return Math.min(pricingSettings.getDynamicSpreadMaxFinal(), baseSpread * currentPolicy.getTaxMultiplier());
    }

    public void recordSale(Shop shop, int amount) {
        String itemName = shop.getName();
        updateActivity(itemName, amount);
        // Atomic read-modify-write — prevents lost updates from concurrent trades
        int newStock = stockLevels.compute(itemName, (key, current) ->
                (current != null ? current : getCurrentStock(shop)) + amount);

        if (globalStockRepository != null) {
            saveAsync(itemName, newStock);
        } else {
            save(); // Force immediate save in YML mode to prevent data loss on reload
        }
    }

    public void recordBuy(Shop shop, int amount) {
        String itemName = shop.getName();
        updateActivity(itemName, amount);
        // Atomic read-modify-write with floor at 0 — prevents lost updates from concurrent trades
        int newStock = stockLevels.compute(itemName, (key, current) ->
                Math.max(0, (current != null ? current : getCurrentStock(shop)) - amount));

        if (globalStockRepository != null) {
            saveAsync(itemName, newStock);
        } else {
            save(); // Force immediate save in YML mode
        }
    }

    private void updateActivity(String itemName, int amount) {
        double alpha = pricingSettings.getActivityAlpha();
        activityScores.compute(itemName, (key, current) ->
                (current != null ? current : 0.0) * (1.0 - alpha) + (amount * alpha));
    }
    
    public void applyExternalSale(String itemName, int amount) {
        stockLevels.compute(itemName, (key, current) ->
                (current != null ? current : 0) + amount);
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