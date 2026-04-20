package com.github.lye.config;

import com.github.lye.config.settings.IEconomicEventSettings;
import com.github.lye.config.settings.IGuiSettings;
import com.github.lye.config.settings.IMessageSettings;
import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.config.settings.IPricingSettings;
import com.github.lye.config.settings.IShopDefinitions;
import com.github.lye.config.settings.IAutosellSettings;
import com.github.lye.config.settings.impl.DefaultEconomicEventSettings;
import com.github.lye.config.settings.impl.DefaultGuiSettings;
import com.github.lye.config.settings.impl.DefaultMessageSettings;
import com.github.lye.config.settings.impl.DefaultPluginSettings;
import com.github.lye.config.settings.impl.DefaultPricingSettings;
import com.github.lye.config.settings.impl.DefaultShopDefinitions;
import com.github.lye.config.settings.impl.DefaultAutosellSettings;
import com.github.lye.data.CollectFirst;
import com.github.lye.data.CollectFirst.CollectFirstSetting;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import com.github.lye.TradeFlow;
import com.github.lye.util.Format;
import com.github.lye.util.TradeFlowLogger;

/**
 * The class for loading and storing the configuration options.
 */
public class Config {

    private static Config config;
    private static TradeFlow pluginInstance;

    private static final String[] filenames = { 
        "config.yml", 
        "shops.yml",
        "data/defaults/playerdata.yml", 
        "messages.yml",
        "modules/pricing.yml",
        "modules/features.yml",
        "modules/organizations.yml",
        "modules/general.yml",
        "modules/families.yml"
    };
    
    private static File[] files;
    private static YamlConfiguration[] configs;

    private final IPluginSettings pluginSettings;
    private final IPricingSettings pricingSettings;
    private final IGuiSettings guiSettings;
    private final IMessageSettings messageSettings;
    private final IShopDefinitions shopDefinitions;
    private final IAutosellSettings autosellSettings;
    private final IEconomicEventSettings economicEventSettings;

    public IMessageSettings getMessageSettings() { return messageSettings; }
    public IShopDefinitions getShopDefinitions() { return shopDefinitions; }
    public IPluginSettings getPluginSettings() { return pluginSettings; }
    public IPricingSettings getPricingSettings() { return pricingSettings; }
    public IGuiSettings getGuiSettings() { return guiSettings; }
    public IAutosellSettings getAutosellSettings() { return autosellSettings; }
    public IEconomicEventSettings getEconomicEventSettings() { return economicEventSettings; }

    public static void init(TradeFlow tradeFlow, TradeFlowLogger logger) {
        pluginInstance = tradeFlow;
        try {
            initializeConfig(logger);
        } catch (Exception e) {
            logger.severe("Failed to initialize config: " + e.getMessage());
            
            // Critical Fallback: Ensure config is not null to prevent NPEs downstream
            if (config == null) {
                logger.severe("Activating EMERGENCY FALLBACK for Config.");
                files = new File[filenames.length];
                configs = new YamlConfiguration[filenames.length]; 
                for (int i = 0; i < configs.length; i++) configs[i] = new YamlConfiguration();
                // Call safe mode constructor
                config = new Config(configs, files, logger, true);
            }
        }
    }

    private static void initializeConfig(TradeFlowLogger logger) {
        files = new File[filenames.length];
        configs = new YamlConfiguration[filenames.length];
        
        // Ensure initial non-null state
        for (int i = 0; i < configs.length; i++) {
            configs[i] = new YamlConfiguration();
        }
        
        config = new Config(configs, files, logger, false);
    }

    private static YamlConfiguration getOrCreateConfigAt(int index) {
        if (configs == null || configs.length != filenames.length) {
            configs = new YamlConfiguration[filenames.length];
        }
        if (configs[index] == null) {
            configs[index] = new YamlConfiguration();
        }
        return configs[index];
    }

    public static YamlConfiguration getShopsConfig() { return getOrCreateConfigAt(1); }
    public static YamlConfiguration getConfigConfig() { return getOrCreateConfigAt(0); }
    public static YamlConfiguration getMessagesConfig() { return getOrCreateConfigAt(3); }
    public static YamlConfiguration getPlayerdataConfig() { return getOrCreateConfigAt(2); }
    public static YamlConfiguration getPricingModule() { return getOrCreateConfigAt(4); }
    public static YamlConfiguration getFeaturesModule() { return getOrCreateConfigAt(5); }
    public static YamlConfiguration getOrgsModule() { return getOrCreateConfigAt(6); }
    public static YamlConfiguration getGeneralModule() { return getOrCreateConfigAt(7); }
    public static YamlConfiguration getFamiliesModule() { return getOrCreateConfigAt(8); }

    public static Config get() { return config; }

    private Config(YamlConfiguration[] configs, File[] files, TradeFlowLogger logger, boolean safeMode) {
        TradeFlow instance = pluginInstance;
        
        // If safeMode is true, skip all IO and logic, just initialize empty settings
        if (safeMode) {
            this.pluginSettings = new DefaultPluginSettings(configs[7], configs[4], configs[5], logger);
            this.pricingSettings = new DefaultPricingSettings(configs[4], logger);
            this.guiSettings = new DefaultGuiSettings(configs[7], logger);
            this.messageSettings = new DefaultMessageSettings(configs[3]);
            this.shopDefinitions = new DefaultShopDefinitions(configs[1], logger);
            this.autosellSettings = new DefaultAutosellSettings(configs[2], instance != null ? new File(instance.getDataFolder(), "playerdata.yml") : null, logger);
            this.economicEventSettings = new DefaultEconomicEventSettings(configs[4], logger, instance);
            return;
        }

        for (int i = 0; i < filenames.length; i++) {
            File targetFile = new File(instance.getDataFolder(), filenames[i]);
            if (!targetFile.exists()) {
                if (filenames[i].contains("/")) {
                    targetFile.getParentFile().mkdirs();
                }
                try {
                    instance.saveResource(filenames[i], false);
                } catch (IllegalArgumentException e) {
                    logger.warning("Could not save resource " + filenames[i] + ": " + e.getMessage());
                }
            }
        }

        saveWebFolder();

        // 1. Load root config (config.yml) first to read module paths
        File configFile = new File(instance.getDataFolder(), "config.yml");
        if (configFile.exists()) {
             files[0] = configFile;
             configs[0] = loadYamlWithRecovery(configFile, "config.yml", "config.yml", logger);
        }

        // 2. Parse module redirections
        java.util.Map<String, String> modulePaths = new java.util.HashMap<>();
        if (configs[0] != null) {
            ConfigurationSection modules = configs[0].getConfigurationSection("modules");
            if (modules != null) {
                for (String key : modules.getKeys(false)) {
                    modulePaths.put(key, modules.getString(key));
                }
            }
        }

        for (int i = 0; i < filenames.length; i++) {
            // Skip config.yml as it is already loaded
            if (i == 0) continue;

            String filename = filenames[i];
            
            // Check for module redirection
            if (i == 4 && modulePaths.get("pricing") != null) filename = modulePaths.get("pricing");
            if (i == 5 && modulePaths.get("features") != null) filename = modulePaths.get("features");
            if (i == 6 && modulePaths.get("organizations") != null) filename = modulePaths.get("organizations");
            if (i == 7 && modulePaths.get("general") != null) filename = modulePaths.get("general");
            if (i == 8 && modulePaths.get("families") != null) filename = modulePaths.get("families");

            File file = new File(instance.getDataFolder(), filename);
            if (!file.exists()) {
                // Determine if we should fail or try default
                File defaultFile = new File(instance.getDataFolder(), filenames[i]);
                if (defaultFile.exists()) {
                    file = defaultFile;
                    logger.info("Module file " + filename + " not found, falling back to " + filenames[i]);
                } else {
                    // Critical fallback: Try to save the resource again if it matches a default filename
                    if (filename.equals(filenames[i])) {
                        try {
                            if (filename.contains("/")) file.getParentFile().mkdirs();
                            instance.saveResource(filename, false);
                            logger.info("Restored missing default config file: " + filename);
                        } catch (Exception e) {
                             logger.warning("Could not restore " + filename + ": " + e.getMessage());
                        }
                    }
                    
                    if (!file.exists()) {
                        instance.getLogger().info("Failed to load config file: " + filename + " (Path: " + file.getAbsolutePath() + ")");
                        // Initialize empty config to prevent NPE
                        configs[i] = new YamlConfiguration();
                        files[i] = file;
                        continue;
                    }
                }
            }
            // Ensure files[i] is assigned before loading
            files[i] = file;
            configs[i] = loadYamlWithRecovery(files[i], filename, filenames[i], logger);
        }
        
        // Ensure ALL configs are initialized, even if loop logic missed something (i.e., config.yml if not exists)
        for (int i = 0; i < filenames.length; i++) {
             if (configs[i] == null) {
                 logger.warning("Config index " + i + " was null. Initializing empty config.");
                 configs[i] = new YamlConfiguration();
             }
        }
        
        // 3. Special check for messages redirection within general module (OR theme preset)
        if (configs[7] != null) {
             String theme = configs[7].getString("gui.theme", "default");
             String msgFile = configs[7].getString("system.messages-file"); // Manual override
             
             if ("violet".equalsIgnoreCase(theme)) {
                 msgFile = "messages_violet.yml";
                 logger.info("Applying VIOLET theme for messages.");
             }
             
             if (msgFile != null) {
                 File target = new File(instance.getDataFolder(), msgFile);
                 if (target.exists()) {
                     files[3] = target;
                     configs[3] = loadYamlWithRecovery(target, msgFile, "messages.yml", logger);
                     logger.info("Loaded custom messages file: " + msgFile);
                 } else {
                     // Try to load from resource if not found in data folder (first run)
                      try {
                          if (instance.getResource(msgFile) != null) {
                              instance.saveResource(msgFile, false);
                              files[3] = target;
                              configs[3] = loadYamlWithRecovery(target, msgFile, "messages.yml", logger);
                              logger.info("Created and loaded custom messages file: " + msgFile);
                          }
                      } catch (Exception ignored) {}
                 }
             }
        }

        this.pluginSettings = new DefaultPluginSettings(configs[7], configs[4], configs[5], logger);
        this.pricingSettings = new DefaultPricingSettings(configs[4], logger);
        this.guiSettings = new DefaultGuiSettings(configs[7], logger);
        this.messageSettings = new DefaultMessageSettings(configs[3]);
        this.shopDefinitions = new DefaultShopDefinitions(configs[1], logger);
        this.autosellSettings = new DefaultAutosellSettings(configs[2], files[2], logger);
        this.economicEventSettings = new DefaultEconomicEventSettings(configs[4], logger, instance);
    }

    public String getNotInShop() { return messageSettings.getNotInShop(); }
    public String getRunOutOfSells() { return messageSettings.getRunOutOfSells(); }
    public String getRunOutOfBuys() { return messageSettings.getRunOutOfBuys(); }
    public String getNotEnoughMoney() { return messageSettings.getNotEnoughMoney(); }
    public boolean isEnableSellLimits() { return pluginSettings.isEnableSellLimits(); }
    public String getGuiLockedStyle() { return guiSettings.getGuiLockedStyle(); }
    public boolean isDurabilityFunction() { return pluginSettings.isDurabilityFunction(); }
    public boolean isEnableDynamicPricing() { return pluginSettings.isEnableDynamicPricing(); }
    public String getCentralBankAccount() { return pluginSettings.getCentralBankAccount(); }
    public double getInitialCapital() { return pluginSettings.getInitialCapital(); }
    public void setAutosell(@NotNull ConfigurationSection section) { autosellSettings.setAutosell(section); }

    private void saveWebFolder() {
        TradeFlow instance = pluginInstance;
        File webFolder = new File(instance.getDataFolder(), "web");
        if (!webFolder.exists()) webFolder.mkdir();
        File buildFolder = new File(webFolder, "build");
        if (!buildFolder.exists()) buildFolder.mkdir();
        String[] files = { "index.html" };
        for (String file : files) {
            File webFile = new File(webFolder, file);
            if (!webFile.exists()) {
                try {
                    instance.saveResource("web/" + file, true);
                } catch (IllegalArgumentException e) {
                    TradeFlowLogger logger = instance.getTradeLogger();
                    if (logger != null) {
                        logger.warning("Could not save web resource: " + file + " (Not found in JAR)");
                    }
                }
            }
        }
    }

    private YamlConfiguration loadYamlWithRecovery(File file, String logicalName, String defaultResourceName, TradeFlowLogger logger) {
        YamlConfiguration loaded = new YamlConfiguration();
        if (file == null || !file.exists()) {
            return loaded;
        }

        String content;
        try {
            content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException ioEx) {
            logger.warning("Could not read config file " + logicalName + ": " + ioEx.getMessage());
            return loaded;
        }

        try {
            loaded.loadFromString(content);
            return loaded;
        } catch (Exception parseEx) {
            String sanitized = sanitizeYamlContent(content);
            if (!sanitized.equals(content)) {
                try {
                    YamlConfiguration sanitizedConfig = new YamlConfiguration();
                    sanitizedConfig.loadFromString(sanitized);
                    backupCorruptedFile(file.toPath(), logger);
                    Files.writeString(file.toPath(), sanitized, StandardCharsets.UTF_8);
                    logger.warning("Recovered invalid YAML characters in " + logicalName + " and rewrote sanitized content.");
                    return sanitizedConfig;
                } catch (Exception sanitizeEx) {
                    logger.warning("Sanitized YAML still invalid for " + logicalName + ": " + sanitizeEx.getMessage());
                }
            }

            backupCorruptedFile(file.toPath(), logger);
            logger.warning("Cannot parse " + logicalName + " (" + parseEx.getMessage() + "). Trying default resource " + defaultResourceName + ".");

            TradeFlow instance = pluginInstance;
            File defaultFile = new File(instance.getDataFolder(), defaultResourceName);
            if (!defaultFile.exists()) {
                try {
                    instance.saveResource(defaultResourceName, false);
                } catch (Exception saveEx) {
                    logger.warning("Could not restore default resource " + defaultResourceName + ": " + saveEx.getMessage());
                }
            }

            if (defaultFile.exists()) {
                try {
                    String defaultContent = Files.readString(defaultFile.toPath(), StandardCharsets.UTF_8);
                    YamlConfiguration defaultCfg = new YamlConfiguration();
                    defaultCfg.loadFromString(sanitizeYamlContent(defaultContent));
                    logger.warning("Loaded fallback config from " + defaultResourceName + " for " + logicalName + ".");
                    return defaultCfg;
                } catch (Exception defaultEx) {
                    logger.warning("Fallback resource is invalid for " + logicalName + ": " + defaultEx.getMessage());
                }
            }

            return new YamlConfiguration();
        }
    }

    private String sanitizeYamlContent(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String sanitized = input;

        sanitized = sanitized.replace("\0", "");
        sanitized = sanitized.replaceAll("!![a-zA-Z0-9_.-]+", "");
        sanitized = sanitized.replace("{{", "").replace("}}", "");

        StringBuilder sb = new StringBuilder(sanitized.length());
        for (int i = 0; i < sanitized.length(); i++) {
            char c = sanitized.charAt(i);
            if (c == '\t' || c == '\n' || c == '\r' || c >= 0x20) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private void backupCorruptedFile(Path sourcePath, TradeFlowLogger logger) {
        try {
            TradeFlow instance = pluginInstance;
            Path backupDir = instance.getDataFolder().toPath().resolve("backups").resolve("config-corrupt");
            Files.createDirectories(backupDir);
            String ts = String.valueOf(System.currentTimeMillis());
            Path backup = backupDir.resolve(sourcePath.getFileName().toString() + "." + ts + ".broken");
            Files.copy(sourcePath, backup);
        } catch (Exception backupEx) {
            logger.warning("Could not backup corrupted config file " + sourcePath.getFileName() + ": " + backupEx.getMessage());
        }
    }
}