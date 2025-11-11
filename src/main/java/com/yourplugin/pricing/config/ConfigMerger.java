package com.yourplugin.pricing.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public class ConfigMerger {

    private static final Logger LOGGER = Logger.getLogger(ConfigMerger.class.getName());

    /**
     * Loads and deep merges a primary configuration file over a default configuration file.
     * Scalars in the primary config override those in the default. Maps are merged recursively.
     * Lists are replaced by the primary config's list.
     *
     * @param defaultConfigFile The default configuration file (e.g., config.yml).
     * @param primaryConfigFile The primary configuration file (e.g., shop.yml), which takes precedence.
     * @return A merged YamlConfiguration.
     */
    public static YamlConfiguration mergeConfigs(File defaultConfigFile, File primaryConfigFile) {
        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(defaultConfigFile);
        YamlConfiguration primaryConfig = YamlConfiguration.loadConfiguration(primaryConfigFile);

        int overrides = deepMerge(defaultConfig, primaryConfig);

        LOGGER.info(String.format("Merged config (shop > config): %d overrides applied.", overrides));
        return defaultConfig;
    }

    /**
     * Recursively merges the 'from' configuration section into the 'to' configuration section.
     * Values in 'from' override values in 'to'.
     *
     * @param to The configuration section to merge into.
     * @param from The configuration section to merge from.
     * @return The number of overrides applied.
     */
    private static int deepMerge(ConfigurationSection to, ConfigurationSection from) {
        int overrides = 0;
        for (String key : from.getKeys(false)) {
            if (from.isConfigurationSection(key)) {
                ConfigurationSection toSection = to.getConfigurationSection(key);
                if (toSection == null) {
                    toSection = to.createSection(key);
                }
                overrides += deepMerge(toSection, from.getConfigurationSection(key));
            } else {
                // Scalars (string/int/double/bool) and lists are replaced by the primary config.
                if (to.contains(key) && !Objects.equals(to.get(key), from.get(key))) {
                    overrides++;
                }
                to.set(key, from.get(key));
            }
        }
        return overrides;
    }

    /**
     * Saves the merged configuration to a file.
     * @param config The configuration to save.
     * @param file The file to save to.
     */
    public static void saveConfig(YamlConfiguration config, File file) {
        try {
            config.save(file);
        } catch (IOException e) {
            LOGGER.severe("Could not save configuration to " + file.getAbsolutePath() + ": " + e.getMessage());
        }
    }
}