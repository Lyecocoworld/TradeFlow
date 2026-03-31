package com.github.lye.market;

import com.github.lye.TradeFlow;
import com.github.lye.repository.ServerStateRepository;
import com.github.lye.util.FoliaSchedulers;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MarketTrendManager {

    private static final Logger LOGGER = Logger.getLogger(MarketTrendManager.class.getName());

    private final TradeFlow plugin;
    private final ServerStateRepository repository; // Can be null
    private final Random random = new Random();

    // Cache
    private final Map<String, Double> weeklySectionTrends = new HashMap<>();
    private final Map<String, Double> specificItemTrends = new HashMap<>(); // New: Daily specific items
    private double monthlyGlobalTrend = 1.0;
    private final Map<String, Double> dailyNoise = new HashMap<>();

    // Time keys
    private static final String KEY_WEEKLY_RESET = "trend_weekly_reset";
    private static final String KEY_MONTHLY_RESET = "trend_monthly_reset";
    private static final String KEY_DAILY_RESET = "trend_daily_reset";

    // Data keys prefix
    private static final String PREFIX_WEEKLY = "trend_val_weekly_";
    private static final String PREFIX_SPECIFIC = "trend_val_specific_";
    private static final String KEY_MONTHLY_VAL = "trend_val_monthly";

    // File persistence
    private static final long SAVE_INTERVAL_TICKS = 6000L; // 5 minutes (300s * 20 ticks)
    private final File trendsFile;

    public MarketTrendManager(TradeFlow plugin, ServerStateRepository repository) {
        this.plugin = plugin;
        this.repository = repository;

        // Ensure data directory exists
        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        this.trendsFile = new File(dataDir, "market_trends.yml");

        // Load persisted trends from file BEFORE generating/validating
        loadFromFile();
        loadOrGenerateTrends();

        // Start periodic file save every 5 minutes
        startPeriodicSave();
    }

    public void checkUpdates() {
        long now = System.currentTimeMillis();
        boolean updated = false;

        if (now >= getNextReset(KEY_DAILY_RESET)) {
            generateDailyNoise();
            generateSpecificItemTrends(); // Generate hot items daily
            setNextReset(KEY_DAILY_RESET, now + 86400000L); // +24h
            updated = true;
        }

        if (now >= getNextReset(KEY_WEEKLY_RESET)) {
            generateWeeklyTrends();
            setNextReset(KEY_WEEKLY_RESET, now + 604800000L); // +7 days
            updated = true;
        }

        if (now >= getNextReset(KEY_MONTHLY_RESET)) {
            generateMonthlyTrend();
            setNextReset(KEY_MONTHLY_RESET, now + 2592000000L); // +30 days
            updated = true;
        }

        if (updated) {
            saveTrends();
            plugin.recalculatePrices(); // Trigger price update
        }
    }

    private void loadOrGenerateTrends() {
        // Try to load from repo/file
        if (repository != null) {
            // Load Monthly
            String mVal = repository.getState(KEY_MONTHLY_VAL);
            monthlyGlobalTrend = mVal != null ? Double.parseDouble(mVal) : 1.0;
            
            // Load Specific Items (Need a way to store list, for now simplified: regeneration on reboot if DB lacks keys isn't ideal but keeps it simple. 
            // Better: We generate if daily reset passed.)
        } 
        
        // Initial generation if needed (or first run)
        long now = System.currentTimeMillis();
        if (getNextReset(KEY_DAILY_RESET) <= now) { // Changed == 0 to <= now to catch up
            generateDailyNoise();
            generateSpecificItemTrends();
            setNextReset(KEY_DAILY_RESET, now + 86400000L);
        }
        if (getNextReset(KEY_WEEKLY_RESET) <= now) {
            generateWeeklyTrends();
            setNextReset(KEY_WEEKLY_RESET, now + 604800000L);
        }
        if (getNextReset(KEY_MONTHLY_RESET) <= now) {
            generateMonthlyTrend();
            setNextReset(KEY_MONTHLY_RESET, now + 2592000000L);
        }
    }

    private void generateSpecificItemTrends() {
        specificItemTrends.clear();
        // Pick random items to be "Hot" or "Crash"
        // 1. Get all shops
        java.util.List<String> allItems = new java.util.ArrayList<>(plugin.getLoadedShops().keySet());
        if (allItems.isEmpty()) return;

        // 2. Pick 5-10 items
        int count = 5 + random.nextInt(6); // 5 to 10
        for (int i = 0; i < count; i++) {
            String item = allItems.get(random.nextInt(allItems.size()));
            
            // 3. Strong Impact: +/- 25% to 40%
            // 0.60 to 0.75 OR 1.25 to 1.40
            boolean positive = random.nextBoolean();
            double trend;
            if (positive) {
                trend = 1.25 + (random.nextDouble() * 0.15);
            } else {
                trend = 0.60 + (random.nextDouble() * 0.15);
            }
            
            specificItemTrends.put(item, trend);
        }
    }

    private void generateDailyNoise() {
        dailyNoise.clear();
        // Noise is small: +/- 2%
        for (String section : getSections()) {
            double noise = 1.0 + (random.nextDouble() * 0.04 - 0.02);
            dailyNoise.put(section, noise);
        }
    }

    private void generateWeeklyTrends() {
        weeklySectionTrends.clear();
        // Weekly is medium: +/- 15%
        for (String section : getSections()) {
            double trend = 1.0 + (random.nextDouble() * 0.30 - 0.15);
            weeklySectionTrends.put(section, trend);
            if (repository != null) {
                repository.setState(PREFIX_WEEKLY + section, String.valueOf(trend));
            }
        }
    }

    private void generateMonthlyTrend() {
        // Monthly is global: +/- 10% inflation/deflation
        monthlyGlobalTrend = 1.0 + (random.nextDouble() * 0.20 - 0.10);
        if (repository != null) {
            repository.setState(KEY_MONTHLY_VAL, String.valueOf(monthlyGlobalTrend));
        }
    }

    private void saveTrends() {
        // Persist via repository (MySQL or FileServerStateRepository)
        if (repository != null) {
            repository.setState(KEY_MONTHLY_VAL, String.valueOf(monthlyGlobalTrend));
            for (Map.Entry<String, Double> entry : weeklySectionTrends.entrySet()) {
                repository.setState(PREFIX_WEEKLY + entry.getKey(), String.valueOf(entry.getValue()));
            }
            for (Map.Entry<String, Double> entry : specificItemTrends.entrySet()) {
                repository.setState(PREFIX_SPECIFIC + entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        // Always save to dedicated file for redundancy
        saveToFile();
    }

    public double getTrend(String section, String itemKey) {
        // 1. Specific Item Trend (Strongest, overrides section if present? Or Multiplies?)
        // Let's Multiply. If Sector is UP but Item is CRASH, it balances or crashes.
        
        double specific = specificItemTrends.getOrDefault(itemKey, 1.0);
        double daily = dailyNoise.getOrDefault(section, 1.0);
        double weekly = getWeeklyTrend(section);               

        return monthlyGlobalTrend * weekly * daily * specific;
    }
    
    public Double getSpecificTrend(String itemKey) {
        return specificItemTrends.get(itemKey);
    }
    
    public double getMonthlyTrend() { return monthlyGlobalTrend; }
    
    public double getWeeklyTrend(String section) {
        if (!weeklySectionTrends.containsKey(section) || weeklySectionTrends.get(section) == 1.0) {
           // Try to load from repo first
           if (repository != null) {
               String val = repository.getState(PREFIX_WEEKLY + section);
               // Filter out "1.0" or "1.00" which are default placeholders
               if (val != null && !val.startsWith("1.0")) {
                   weeklySectionTrends.put(section, Double.parseDouble(val));
               } else {
                   // Generate new unique trend for this section
                   double trend = 1.0 + (random.nextDouble() * 0.30 - 0.15);
                   if (trend == 1.0) trend += 0.01; // Avoid strict 1.0
                   weeklySectionTrends.put(section, trend);
                   repository.setState(PREFIX_WEEKLY + section, String.valueOf(trend));
               }
           } else {
               // No repo, just generate ephemeral
                double trend = 1.0 + (random.nextDouble() * 0.30 - 0.15);
                weeklySectionTrends.put(section, trend);
           }
        }
        return weeklySectionTrends.getOrDefault(section, 1.0);
    }

    private long getNextReset(String key) {
        if (repository != null) {
            String val = repository.getState(key);
            return val != null ? Long.parseLong(val) : 0;
        }
        return 0;
    }

    private void setNextReset(String key, long timestamp) {
        if (repository != null) {
            repository.setState(key, String.valueOf(timestamp));
        }
    }

    private java.util.Set<String> getSections() {
        java.util.Set<String> sections = new java.util.HashSet<>();
        
        // 1. Gets from GUI Settings
        if (plugin.getGuiSettings() != null) {
            sections.addAll(plugin.getGuiSettings().getSectionIds());
        }
        
        // 2. Gets from ShopUtil (in case GUI settings are partial)
        if (plugin.getShopUtil() != null) {
             String[] names = plugin.getShopUtil().getSectionNames();
             if (names != null) {
                 java.util.Collections.addAll(sections, names);
             }
        }
        
        return sections;
    }

    // ==================== File-Based Persistence ====================

    /**
     * Starts the periodic file save task using Folia-compatible scheduling.
     */
    private void startPeriodicSave() {
        FoliaSchedulers.runGlobalFixedRate(
                plugin,
                this::saveToFile,
                SAVE_INTERVAL_TICKS,
                SAVE_INTERVAL_TICKS
        );
        LOGGER.fine("Market trends periodic file save started (every 5 minutes)");
    }

    /**
     * Saves all trend data to the YAML file.
     * <p>
     * Persists monthly, weekly, daily, and specific item trend values
     * along with reset timestamps. This acts as a backup/redundancy
     * layer alongside the primary ServerStateRepository.</p>
     */
    public void saveToFile() {
        try {
            YamlConfiguration config = new YamlConfiguration();

            // Monthly global trend
            config.set("monthly-global-trend", monthlyGlobalTrend);

            // Weekly section trends
            for (Map.Entry<String, Double> entry : weeklySectionTrends.entrySet()) {
                config.set("weekly-section-trends." + entry.getKey(), entry.getValue());
            }

            // Specific item trends
            for (Map.Entry<String, Double> entry : specificItemTrends.entrySet()) {
                config.set("specific-item-trends." + entry.getKey(), entry.getValue());
            }

            // Daily noise
            for (Map.Entry<String, Double> entry : dailyNoise.entrySet()) {
                config.set("daily-noise." + entry.getKey(), entry.getValue());
            }

            // Reset timestamps
            if (repository != null) {
                String dailyReset = repository.getState(KEY_DAILY_RESET);
                String weeklyReset = repository.getState(KEY_WEEKLY_RESET);
                String monthlyReset = repository.getState(KEY_MONTHLY_RESET);

                if (dailyReset != null) config.set("resets.daily", Long.parseLong(dailyReset));
                if (weeklyReset != null) config.set("resets.weekly", Long.parseLong(weeklyReset));
                if (monthlyReset != null) config.set("resets.monthly", Long.parseLong(monthlyReset));
            }

            config.save(trendsFile);
            LOGGER.fine("Market trends saved to file: " + trendsFile.getName());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save market trends to file", e);
        }
    }

    /**
     * Loads trend data from the YAML file.
     * <p>
     * Restores previously saved trend values. This is called during
     * initialization before {@link #loadOrGenerateTrends()}, so that
     * existing trends are preserved if no reset is due.</p>
     */
    private void loadFromFile() {
        if (!trendsFile.exists()) {
            LOGGER.fine("No market trends file found — will generate fresh trends");
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(trendsFile);

            // Monthly global trend
            if (config.contains("monthly-global-trend")) {
                monthlyGlobalTrend = config.getDouble("monthly-global-trend", 1.0);
            }

            // Weekly section trends
            if (config.getConfigurationSection("weekly-section-trends") != null) {
                for (String key : config.getConfigurationSection("weekly-section-trends").getKeys(false)) {
                    weeklySectionTrends.put(key, config.getDouble("weekly-section-trends." + key, 1.0));
                }
            }

            // Specific item trends
            if (config.getConfigurationSection("specific-item-trends") != null) {
                for (String key : config.getConfigurationSection("specific-item-trends").getKeys(false)) {
                    specificItemTrends.put(key, config.getDouble("specific-item-trends." + key, 1.0));
                }
            }

            // Daily noise
            if (config.getConfigurationSection("daily-noise") != null) {
                for (String key : config.getConfigurationSection("daily-noise").getKeys(false)) {
                    dailyNoise.put(key, config.getDouble("daily-noise." + key, 1.0));
                }
            }

            LOGGER.info("Market trends loaded from file (" + weeklySectionTrends.size()
                    + " weekly, " + specificItemTrends.size() + " specific, "
                    + dailyNoise.size() + " daily noise)");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load market trends from file — will regenerate", e);
        }
    }

    /**
     * Shuts down the market trend manager, performing a final save to file.
     */
    public void shutdown() {
        saveToFile();
        LOGGER.info("MarketTrendManager shut down — final save complete");
    }
}
