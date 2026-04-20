package com.github.lye.market;

import com.github.lye.TradeFlow;
import com.github.lye.config.settings.IGuiSettings;
import com.github.lye.data.Database;
import com.github.lye.data.ShopUtil;
import com.github.lye.repository.ServerStateRepository;
import com.github.lye.util.FoliaSchedulers;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MarketTrendManager {

    private static final Logger LOGGER = Logger.getLogger(MarketTrendManager.class.getName());

    private final TradeFlow plugin;
    private final ServerStateRepository repository;
    private final Random random = new Random();

    private final Map<String, Double> weeklySectionTrends = new ConcurrentHashMap<>();
    private final Map<String, Double> specificItemTrends = new ConcurrentHashMap<>();
    private volatile double monthlyGlobalTrend = 1.0;
    private final Map<String, Double> dailyNoise = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Long> resetTimestamps = new ConcurrentHashMap<>();

    private static final String KEY_WEEKLY_RESET = "trend_weekly_reset";
    private static final String KEY_MONTHLY_RESET = "trend_monthly_reset";
    private static final String KEY_DAILY_RESET = "trend_daily_reset";

    private static final String PREFIX_WEEKLY = "trend_val_weekly_";
    private static final String PREFIX_SPECIFIC = "trend_val_specific_";
    private static final String KEY_MONTHLY_VAL = "trend_val_monthly";

    private static final long SAVE_INTERVAL_TICKS = 6000L;
    private final File trendsFile;

    public MarketTrendManager(TradeFlow plugin, ServerStateRepository repository) {
        this.plugin = plugin;
        this.repository = repository;

        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        this.trendsFile = new File(dataDir, "market_trends.yml");

        loadFromFile();
        loadOrGenerateTrends();
        startPeriodicSave();
    }

    public void checkUpdates() {
        long now = System.currentTimeMillis();
        boolean updated = false;

        if (now >= getNextReset(KEY_DAILY_RESET)) {
            generateDailyNoise();
            generateSpecificItemTrends();
            setNextReset(KEY_DAILY_RESET, now + 86400000L);
            updated = true;
        }

        if (now >= getNextReset(KEY_WEEKLY_RESET)) {
            generateWeeklyTrends();
            setNextReset(KEY_WEEKLY_RESET, now + 604800000L);
            updated = true;
        }

        if (now >= getNextReset(KEY_MONTHLY_RESET)) {
            generateMonthlyTrend();
            setNextReset(KEY_MONTHLY_RESET, now + 2592000000L);
            updated = true;
        }

        if (updated) {
            saveTrends();
            plugin.recalculatePrices();
        }
    }

    private void loadOrGenerateTrends() {
        if (repository != null) {
            repository.getState(KEY_MONTHLY_VAL)
                    .thenAccept(mVal -> {
                        if (mVal != null) {
                            try {
                                monthlyGlobalTrend = Double.parseDouble(mVal);
                            } catch (NumberFormatException e) {
                                LOGGER.warning("[MarketTrends] Invalid monthly value: " + mVal);
                            }
                        }
                        generateIfNeeded();
                    })
                    .exceptionally(ex -> {
                        LOGGER.log(Level.WARNING, "[MarketTrends] Failed to load monthly trend, using default", ex);
                        generateIfNeeded();
                        return null;
                    });
        } else {
            generateIfNeeded();
        }
    }

    private void generateIfNeeded() {
        long now = System.currentTimeMillis();
        if (getNextReset(KEY_DAILY_RESET) <= now) {
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
        java.util.List<String> allItems = new java.util.ArrayList<>(plugin.getServices().get(Database.class).getShops().keySet());
        if (allItems.isEmpty()) return;

        int count = 5 + random.nextInt(6);
        for (int i = 0; i < count; i++) {
            String item = allItems.get(random.nextInt(allItems.size()));

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
        for (String section : getSections()) {
            double noise = 1.0 + (random.nextDouble() * 0.04 - 0.02);
            dailyNoise.put(section, noise);
        }
    }

    private void generateWeeklyTrends() {
        weeklySectionTrends.clear();
        for (String section : getSections()) {
            double trend = 1.0 + (random.nextDouble() * 0.30 - 0.15);
            weeklySectionTrends.put(section, trend);
            if (repository != null) {
                repository.setState(PREFIX_WEEKLY + section, String.valueOf(trend));
            }
        }
    }

    private void generateMonthlyTrend() {
        monthlyGlobalTrend = 1.0 + (random.nextDouble() * 0.20 - 0.10);
        if (repository != null) {
            repository.setState(KEY_MONTHLY_VAL, String.valueOf(monthlyGlobalTrend));
        }
    }

    private void saveTrends() {
        if (repository != null) {
            repository.setState(KEY_MONTHLY_VAL, String.valueOf(monthlyGlobalTrend));
            for (Map.Entry<String, Double> entry : weeklySectionTrends.entrySet()) {
                repository.setState(PREFIX_WEEKLY + entry.getKey(), String.valueOf(entry.getValue()));
            }
            for (Map.Entry<String, Double> entry : specificItemTrends.entrySet()) {
                repository.setState(PREFIX_SPECIFIC + entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        saveToFile();
    }

    public double getTrend(String section, String itemKey) {
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
            if (repository != null) {
                loadWeeklyTrendAsync(section);
            } else {
                double trend = 1.0 + (random.nextDouble() * 0.30 - 0.15);
                weeklySectionTrends.put(section, trend);
            }
        }
        return weeklySectionTrends.getOrDefault(section, 1.0);
    }

    private void loadWeeklyTrendAsync(String section) {
        Double existing = weeklySectionTrends.get(section);
        if (existing != null && existing != 1.0) return;

        repository.getState(PREFIX_WEEKLY + section)
                .thenAccept(val -> {
                    if (val != null && !val.startsWith("1.0")) {
                        weeklySectionTrends.put(section, Double.parseDouble(val));
                    } else {
                        double trend = 1.0 + (random.nextDouble() * 0.30 - 0.15);
                        if (trend == 1.0) trend += 0.01;
                        weeklySectionTrends.put(section, trend);
                        repository.setState(PREFIX_WEEKLY + section, String.valueOf(trend));
                    }
                })
                .exceptionally(ex -> {
                    double trend = 1.0 + (random.nextDouble() * 0.30 - 0.15);
                    weeklySectionTrends.put(section, trend);
                    return null;
                });
    }

    private long getNextReset(String key) {
        Long cached = resetTimestamps.get(key);
        if (cached != null) return cached;

        if (repository != null) {
            repository.getState(key).thenAccept(val -> {
                if (val != null) {
                    try {
                        resetTimestamps.put(key, Long.parseLong(val));
                    } catch (NumberFormatException e) {
                        LOGGER.warning("[MarketTrends] Invalid reset timestamp for " + key + ": " + val);
                    }
                }
            });
        }
        return 0;
    }

    private void setNextReset(String key, long timestamp) {
        resetTimestamps.put(key, timestamp);
        if (repository != null) {
            repository.setState(key, String.valueOf(timestamp));
        }
    }

    private java.util.Set<String> getSections() {
        java.util.Set<String> sections = new java.util.HashSet<>();

        IGuiSettings guiSettings = plugin.getServices().get(IGuiSettings.class);
        if (guiSettings != null) {
            sections.addAll(guiSettings.getSectionIds());
        }

        ShopUtil shopUtil = plugin.getServices().get(ShopUtil.class);
        if (shopUtil != null) {
             String[] names = shopUtil.getSectionNames();
             if (names != null) {
                 java.util.Collections.addAll(sections, names);
             }
        }

        return sections;
    }

    private void startPeriodicSave() {
        FoliaSchedulers.runGlobalFixedRate(
                plugin,
                this::saveToFile,
                SAVE_INTERVAL_TICKS,
                SAVE_INTERVAL_TICKS
        );
        LOGGER.fine("Market trends periodic file save started (every 5 minutes)");
    }

    public void saveToFile() {
        try {
            YamlConfiguration config = new YamlConfiguration();

            config.set("monthly-global-trend", monthlyGlobalTrend);

            for (Map.Entry<String, Double> entry : weeklySectionTrends.entrySet()) {
                config.set("weekly-section-trends." + entry.getKey(), entry.getValue());
            }

            for (Map.Entry<String, Double> entry : specificItemTrends.entrySet()) {
                config.set("specific-item-trends." + entry.getKey(), entry.getValue());
            }

            for (Map.Entry<String, Double> entry : dailyNoise.entrySet()) {
                config.set("daily-noise." + entry.getKey(), entry.getValue());
            }

            Long dailyReset = resetTimestamps.get(KEY_DAILY_RESET);
            Long weeklyReset = resetTimestamps.get(KEY_WEEKLY_RESET);
            Long monthlyReset = resetTimestamps.get(KEY_MONTHLY_RESET);

            if (dailyReset != null) config.set("resets.daily", dailyReset);
            if (weeklyReset != null) config.set("resets.weekly", weeklyReset);
            if (monthlyReset != null) config.set("resets.monthly", monthlyReset);

            config.save(trendsFile);
            LOGGER.fine("Market trends saved to file: " + trendsFile.getName());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save market trends to file", e);
        }
    }

    private void loadFromFile() {
        if (!trendsFile.exists()) {
            LOGGER.fine("No market trends file found — will generate fresh trends");
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(trendsFile);

            if (config.contains("monthly-global-trend")) {
                monthlyGlobalTrend = config.getDouble("monthly-global-trend", 1.0);
            }

            if (config.getConfigurationSection("weekly-section-trends") != null) {
                for (String key : config.getConfigurationSection("weekly-section-trends").getKeys(false)) {
                    weeklySectionTrends.put(key, config.getDouble("weekly-section-trends." + key, 1.0));
                }
            }

            if (config.getConfigurationSection("specific-item-trends") != null) {
                for (String key : config.getConfigurationSection("specific-item-trends").getKeys(false)) {
                    specificItemTrends.put(key, config.getDouble("specific-item-trends." + key, 1.0));
                }
            }

            if (config.getConfigurationSection("daily-noise") != null) {
                for (String key : config.getConfigurationSection("daily-noise").getKeys(false)) {
                    dailyNoise.put(key, config.getDouble("daily-noise." + key, 1.0));
                }
            }

            if (config.contains("resets.daily")) {
                resetTimestamps.put(KEY_DAILY_RESET, config.getLong("resets.daily"));
            }
            if (config.contains("resets.weekly")) {
                resetTimestamps.put(KEY_WEEKLY_RESET, config.getLong("resets.weekly"));
            }
            if (config.contains("resets.monthly")) {
                resetTimestamps.put(KEY_MONTHLY_RESET, config.getLong("resets.monthly"));
            }

            LOGGER.info("Market trends loaded from file (" + weeklySectionTrends.size()
                    + " weekly, " + specificItemTrends.size() + " specific, "
                    + dailyNoise.size() + " daily noise)");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load market trends from file — will regenerate", e);
        }
    }

    public void shutdown() {
        saveToFile();
        LOGGER.info("MarketTrendManager shut down — final save complete");
    }
}
