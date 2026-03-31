package com.github.lye.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import com.github.lye.config.Config;
import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.config.settings.IPricingSettings;
import com.github.lye.util.TradeFlowLogger;
import com.github.lye.util.Format;

public class Shop implements Serializable {

    private static final long serialVersionUID = -6381163788906178955L;

    private final String name;
    private int[] buys;
    private int[] sells;
    private double[] prices;
    private double basePrice; // NEW: Permanent anchor price from config
    private int size;
    private final boolean enchantment;
    private CollectFirst setting;
    private Map<UUID, Integer> autosell;
    private int totalBuys;
    private int totalSells;
    private boolean locked;
    private double customSpd;
    private double volatility;
    private double change;
    private int maxBuys;
    private int maxSells;
    private int updateRate;
    private int timeSinceUpdate;
    private String section;
    private int globalStockLimit;
    private String globalStockPeriod;
    private Map<UUID, Integer> recentBuys;
    private Map<UUID, Integer> recentSells;
    private String access;
    
    private int currentStock;
    private int minBaseStock;
    private int maxBaseStock;

    private final IPricingSettings pricingSettings;
    private final IPluginSettings pluginSettings;
    private final TradeFlowLogger logger;
    
    // Constructor manually implemented (was AllArgsConstructor)
    public Shop(String name, int[] buys, int[] sells, double[] prices, int size, boolean enchantment, 
                CollectFirst setting, Map<UUID, Integer> autosell, int totalBuys, int totalSells, 
                boolean locked, double customSpd, double volatility, double change, int maxBuys, 
                int maxSells, int updateRate, int timeSinceUpdate, String section, int globalStockLimit, 
                String globalStockPeriod, Map<UUID, Integer> recentBuys, Map<UUID, Integer> recentSells, 
                String access, int currentStock, int minBaseStock, int maxBaseStock, 
                IPricingSettings pricingSettings, IPluginSettings pluginSettings, TradeFlowLogger logger) {
        this.name = name;
        this.buys = buys;
        this.sells = sells;
        this.prices = prices;
        this.size = size;
        this.enchantment = enchantment;
        this.setting = setting;
        this.autosell = autosell;
        this.totalBuys = totalBuys;
        this.totalSells = totalSells;
        this.locked = locked;
        this.customSpd = customSpd;
        this.volatility = volatility;
        this.change = change;
        this.maxBuys = maxBuys;
        this.maxSells = maxSells;
        this.updateRate = updateRate;
        this.timeSinceUpdate = timeSinceUpdate;
        this.section = section;
        this.globalStockLimit = globalStockLimit;
        this.globalStockPeriod = globalStockPeriod;
        this.recentBuys = recentBuys;
        this.recentSells = recentSells;
        this.access = access;
        this.currentStock = currentStock;
        this.minBaseStock = minBaseStock;
        this.maxBaseStock = maxBaseStock;
        this.pricingSettings = pricingSettings;
        this.pluginSettings = pluginSettings;
        this.logger = logger;
    }

    // Getters and Setters
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public int getTotalBuys() { return totalBuys; }
    public void setTotalBuys(int totalBuys) { this.totalBuys = totalBuys; }
    public int getTotalSells() { return totalSells; }
    public void setTotalSells(int totalSells) { this.totalSells = totalSells; }
    public double getCustomSpd() { return customSpd; }
    public void setCustomSpd(double customSpd) { this.customSpd = customSpd; }
    public int getUpdateRate() { return updateRate; }
    public void setUpdateRate(int updateRate) { this.updateRate = updateRate; }
    public int getTimeSinceUpdate() { return timeSinceUpdate; }
    public void setTimeSinceUpdate(int timeSinceUpdate) { this.timeSinceUpdate = timeSinceUpdate; }
    public String getAccess() { return access; }
    public void setAccess(String access) { this.access = access; }
    public boolean isEnchantment() { return enchantment; }
    public boolean isLocked() { return locked; }
    public double getVolatility() { return volatility; }
    public String getSection() { return section; }
    public int getMaxBuys() { return maxBuys; }
    public int getMaxSells() { return maxSells; }
    public int[] getBuys() { return buys; }
    public int[] getSells() { return sells; }
    public double[] getPrices() { return prices; }
    public Map<UUID, Integer> getAutosell() { return autosell; }
    public Map<UUID, Integer> getRecentBuys() { return recentBuys; }
    public Map<UUID, Integer> getRecentSells() { return recentSells; }
    public CollectFirst getSetting() { return setting; }
    public int getGlobalStockLimit() { return globalStockLimit; }
    public String getGlobalStockPeriod() { return globalStockPeriod; }
    public String getName() { return name; }
    public double getChange() { return change; }
    public void setSetting(CollectFirst setting) { this.setting = setting; }
    
    public int getCurrentStock() { return currentStock; }
    public void setCurrentStock(int currentStock) { this.currentStock = currentStock; }
    public int getMinBaseStock() { return minBaseStock; }
    public void setMinBaseStock(int minBaseStock) { this.minBaseStock = minBaseStock; }
    public int getMaxBaseStock() { return maxBaseStock; }
    public void setMaxBaseStock(int maxBaseStock) { this.maxBaseStock = maxBaseStock; }

    // Builder Pattern Implementation
    public static ShopBuilder builder() {
        return new ShopBuilder();
    }

    public static class ShopBuilder {
        private String name;
        private int[] buys;
        private int[] sells;
        private double[] prices;
        private int size;
        private boolean enchantment;
        private CollectFirst setting;
        private Map<UUID, Integer> autosell;
        private int totalBuys;
        private int totalSells;
        private boolean locked;
        private double customSpd;
        private double volatility;
    private volatile double change;
        private int maxBuys;
        private int maxSells;
        private int updateRate;
        private int timeSinceUpdate;
        private String section;
        private int globalStockLimit;
        private String globalStockPeriod;
        private Map<UUID, Integer> recentBuys;
        private Map<UUID, Integer> recentSells;
        private String access;
    private volatile int currentStock;
        private int minBaseStock;
        private int maxBaseStock;
        private IPricingSettings pricingSettings;
        private IPluginSettings pluginSettings;
        private TradeFlowLogger logger;

        ShopBuilder() {}

        public ShopBuilder name(String name) { this.name = name; return this; }
        public ShopBuilder buys(int[] buys) { this.buys = buys; return this; }
        public ShopBuilder sells(int[] sells) { this.sells = sells; return this; }
        public ShopBuilder prices(double[] prices) { this.prices = prices; return this; }
        public ShopBuilder size(int size) { this.size = size; return this; }
        public ShopBuilder enchantment(boolean enchantment) { this.enchantment = enchantment; return this; }
        public ShopBuilder setting(CollectFirst setting) { this.setting = setting; return this; }
        public ShopBuilder autosell(Map<UUID, Integer> autosell) { this.autosell = autosell; return this; }
        public ShopBuilder totalBuys(int totalBuys) { this.totalBuys = totalBuys; return this; }
        public ShopBuilder totalSells(int totalSells) { this.totalSells = totalSells; return this; }
        public ShopBuilder locked(boolean locked) { this.locked = locked; return this; }
        public ShopBuilder customSpd(double customSpd) { this.customSpd = customSpd; return this; }
        public ShopBuilder volatility(double volatility) { this.volatility = volatility; return this; }
        public ShopBuilder change(double change) { this.change = change; return this; }
        public ShopBuilder maxBuys(int maxBuys) { this.maxBuys = maxBuys; return this; }
        public ShopBuilder maxSells(int maxSells) { this.maxSells = maxSells; return this; }
        public ShopBuilder updateRate(int updateRate) { this.updateRate = updateRate; return this; }
        public ShopBuilder timeSinceUpdate(int timeSinceUpdate) { this.timeSinceUpdate = timeSinceUpdate; return this; }
        public ShopBuilder section(String section) { this.section = section; return this; }
        public ShopBuilder globalStockLimit(int globalStockLimit) { this.globalStockLimit = globalStockLimit; return this; }
        public ShopBuilder globalStockPeriod(String globalStockPeriod) { this.globalStockPeriod = globalStockPeriod; return this; }
        public ShopBuilder recentBuys(Map<UUID, Integer> recentBuys) { this.recentBuys = recentBuys; return this; }
        public ShopBuilder recentSells(Map<UUID, Integer> recentSells) { this.recentSells = recentSells; return this; }
        public ShopBuilder access(String access) { this.access = access; return this; }
        public ShopBuilder currentStock(int currentStock) { this.currentStock = currentStock; return this; }
        public ShopBuilder minBaseStock(int minBaseStock) { this.minBaseStock = minBaseStock; return this; }
        public ShopBuilder maxBaseStock(int maxBaseStock) { this.maxBaseStock = maxBaseStock; return this; }
        public ShopBuilder pricingSettings(IPricingSettings pricingSettings) { this.pricingSettings = pricingSettings; return this; }
        public ShopBuilder pluginSettings(IPluginSettings pluginSettings) { this.pluginSettings = pluginSettings; return this; }
        public ShopBuilder logger(TradeFlowLogger logger) { this.logger = logger; return this; }

        public Shop build() {
            return new Shop(name, buys, sells, prices, size, enchantment, setting, autosell, totalBuys, totalSells, 
                            locked, customSpd, volatility, change, maxBuys, maxSells, updateRate, timeSinceUpdate, 
                            section, globalStockLimit, globalStockPeriod, recentBuys, recentSells, access, 
                            currentStock, minBaseStock, maxBaseStock, pricingSettings, pluginSettings, logger);
        }
    }

    public Shop(String name,
                boolean enchantment,
                double startPrice,
                IPricingSettings pricingSettings,
                IPluginSettings pluginSettings,
                TradeFlowLogger logger) {
        this.name = name;
        this.enchantment = enchantment;
        this.buys = new int[]{0};
        this.sells = new int[]{0};
        this.prices = new double[]{startPrice};
        this.basePrice = startPrice; // Set base price here
        this.size = 1;
        this.autosell = new ConcurrentHashMap<>();
        this.recentBuys = new ConcurrentHashMap<>();
        this.recentSells = new ConcurrentHashMap<>();
        this.totalBuys = 0;
        this.totalSells = 0;
        this.customSpd = -1;
        this.change = 0;
        this.maxBuys = -1;
        this.maxSells = -1;
        this.updateRate = 1;
        this.timeSinceUpdate = 0;
        this.pricingSettings = Objects.requireNonNull(pricingSettings, "pricingSettings");
        this.pluginSettings = Objects.requireNonNull(pluginSettings, "pluginSettings");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.volatility = pricingSettings.getVolatility();
        this.locked = false;
        this.section = null;
        this.globalStockLimit = -1;
        this.globalStockPeriod = "";
        this.access = "";
        this.setting = new CollectFirst("NONE");
        this.currentStock = 0;
        this.minBaseStock = -1;
        this.maxBaseStock = -1;
    }

    public void loadConfiguration(ConfigurationSection config, String sectionName) {
        TradeFlowLogger logger = this.logger != null ? this.logger : Format.getLog();
        locked = config.getBoolean("locked", false);
        customSpd = config.getDouble("sell-price-difference", -1);
        volatility = config.getDouble("volatility", pricingSettings.getVolatility());
        section = sectionName;
        maxBuys = config.getInt("max-buy", -1);
        maxSells = config.getInt("max-sell", -1);
        updateRate = config.getInt("update-rate", 1);
        minBaseStock = config.getInt("base-stock-min", -1);
        maxBaseStock = config.getInt("base-stock-max", -1);
        access = config.getString("access", "");

        double startPrice = config.getDouble("price");
        if (startPrice != prices[0]) {
            prices[size - 1] = startPrice;
        }

        globalStockLimit = config.getInt("global-stock-limit", -1);
        globalStockPeriod = config.getString("global-stock-period", "weekly");
    }

    public synchronized double getPrice() { return prices[size - 1]; }
    public synchronized void setPrice(double price) { 
        prices[size - 1] = Math.max(0, price); 
        
        // Update change based on the initial base price from config
        if (this.basePrice > 0) {
            this.change = (prices[size - 1] - this.basePrice) / this.basePrice;
        } else {
            this.change = 0;
        }
    }

    public double getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(double basePrice) {
        this.basePrice = basePrice;
        if (this.prices != null && this.prices.length > 0) {
            this.prices[0] = basePrice;
        }
    }

    /**
     * Resets the base price to the current price. 
     * Used for rolling reference periods (e.g., resets variation every 30m).
     */
    public void syncBasePrice() {
        this.basePrice = getPrice();
        this.change = 0; // Variation resets to 0% at the start of the new period
    }

    public double getSellPrice() { return getPrice() - getPrice() * getSpd() * 0.01; }

    private double getSpd() {
        return customSpd != -1 ? customSpd : pluginSettings.getSellPriceDifference();
    }

    public double strength() {
        int x = 0, y = 1;
        double buy = 0, sell = 0;
        double m = pricingSettings.getPriceStrengthM();
        double z = pricingSettings.getPriceStrengthZ();
        while (y <= size) {
            buy += buys[size - y];
            sell += sells[size - y];
            x++;
            y = (int) Math.round(m * Math.pow(x, z) + 0.5);
        }
        return (buy == 0 && sell == 0) ? 0 : (buy - sell) / (buy + sell);
    }

    public static Shop fromConfig(String name, ConfigurationSection config, String sectionName, boolean enchantment, IPricingSettings pricingSettings, IPluginSettings pluginSettings, TradeFlowLogger logger) {
        boolean isEnchant = enchantment || sectionName.equalsIgnoreCase("enchantments") || sectionName.equalsIgnoreCase("enchantment");
        
        if (!isEnchant && Material.matchMaterial(name.toUpperCase()) == null) {
            if (Enchantment.getByKey(NamespacedKey.minecraft(name.toLowerCase())) != null) {
                isEnchant = true;
            }
        }
        
        if (isEnchant) {
            // Validate Enchantment key
            if (Enchantment.getByKey(NamespacedKey.minecraft(name.toLowerCase())) == null) {
                logger.severe("Invalid enchantment for shop: " + name + ". Skipping.");
                return null;
            }
        } else {
            // Validate Material
            if (Material.matchMaterial(name.toUpperCase()) == null) {
                logger.severe("Invalid material for shop: " + name + ". Skipping.");
                return null;
            }
        }

        double startPrice = config.getDouble("price");
        Shop shop = new Shop(name, isEnchant, startPrice, pricingSettings, pluginSettings, logger);
        shop.loadConfiguration(config, sectionName);
        return shop;
    }

    public static Component getDisplayName(String name, boolean isEnchantment) {
        if (isEnchantment) {
            Enchantment e = Enchantment.getByKey(NamespacedKey.minecraft(name.toLowerCase()));
            return e != null ? e.displayName(1) : Component.text(name);
        }
        Material m = Material.matchMaterial(name.toUpperCase());
        return new ItemStack(m != null ? m : Material.BARRIER).displayName();
    }

    public Shop(String name, ResultSet rs, Gson gson, IPricingSettings pricingSettings, IPluginSettings pluginSettings, TradeFlowLogger logger) throws SQLException {
        this.name = name;
        this.pricingSettings = pricingSettings;
        this.pluginSettings = pluginSettings;
        this.logger = logger;

        double currentPrice = rs.getDouble("price");
        this.basePrice = currentPrice; // Set base price from DB
        
        this.enchantment = rs.getBoolean("enchantment");
        this.locked = rs.getBoolean("locked");
        this.volatility = rs.getDouble("volatility");
        this.section = rs.getString("section");
        this.maxBuys = rs.getInt("max_buys");
        this.maxSells = rs.getInt("max_sells");
        
        Type intArrType = new TypeToken<int[]>(){}.getType();
        Type doubleArrType = new TypeToken<double[]>(){}.getType();
        Type mapType = new TypeToken<Map<UUID, Integer>>(){}.getType();

        String buysJson = rs.getString("buys_history");
        String sellsJson = rs.getString("sells_history");
        String pricesJson = rs.getString("prices_history");
        String autosellJson = rs.getString("autosell");
        String recentBuysJson = rs.getString("recent_buys");
        String recentSellsJson = rs.getString("recent_sells");

        this.buys = buysJson != null ? gson.fromJson(buysJson, intArrType) : null;
        this.sells = sellsJson != null ? gson.fromJson(sellsJson, intArrType) : null;
        this.prices = pricesJson != null ? gson.fromJson(pricesJson, doubleArrType) : null;
        this.autosell = autosellJson != null ? gson.fromJson(autosellJson, mapType) : null;
        this.recentBuys = recentBuysJson != null ? gson.fromJson(recentBuysJson, mapType) : null;
        this.recentSells = recentSellsJson != null ? gson.fromJson(recentSellsJson, mapType) : null;

        if (this.autosell == null) this.autosell = new ConcurrentHashMap<>();
        else this.autosell = new ConcurrentHashMap<>(this.autosell);
        if (this.recentBuys == null) this.recentBuys = new ConcurrentHashMap<>();
        else this.recentBuys = new ConcurrentHashMap<>(this.recentBuys);
        if (this.recentSells == null) this.recentSells = new ConcurrentHashMap<>();
        else this.recentSells = new ConcurrentHashMap<>(this.recentSells);

        try {
            String coll = rs.getString("collect_first_setting");
            this.setting = new CollectFirst(coll != null ? coll : "NONE");
        } catch (Exception e) {
            this.setting = new CollectFirst("NONE");
        }

        this.currentStock = rs.getInt("current_stock");
        this.minBaseStock = rs.getInt("min_base_stock");
        this.maxBaseStock = rs.getInt("max_base_stock");
        
        if (this.buys == null) this.buys = new int[]{0};
        if (this.sells == null) this.sells = new int[]{0};
        if (this.prices == null || this.prices.length == 0) this.prices = new double[]{currentPrice};
        
        this.size = this.prices.length;
    }

    public void updateChange() {
        if (this.basePrice > 0) {
            this.change = (getPrice() - this.basePrice) / this.basePrice;
        } else if (prices != null && prices.length > 1) {
             double prev = prices[prices.length - 2];
             double curr = prices[prices.length - 1];
             if (prev != 0) {
                 this.change = (curr - prev) / prev;
             } else {
                 this.change = 0;
             }
        }
    }

    public void clearAutosell() {
        this.autosell.clear();
    }

    public void addAutosell(UUID uuid, int amount) {
        this.autosell.merge(uuid, amount, Integer::sum);
    }

    public synchronized void addSells(UUID uuid, int amount) {
        this.totalSells += amount;
        this.recentSells.merge(uuid, amount, Integer::sum);
    }

    public synchronized void addBuys(UUID uuid, int amount) {
        this.totalBuys += amount;
        this.recentBuys.merge(uuid, amount, Integer::sum);
    }

    public void resetDailyLimits() {
        this.recentBuys.clear();
        this.recentSells.clear();
        this.logger.info("Daily limits reset for shop: " + this.name);
    }
}