package com.github.lye.bootstrap;

import com.github.lye.TradeFlow;
import com.github.lye.config.Config;
import com.github.lye.config.settings.*;

/**
 * Service responsible for loading all configuration modules.
 * <p>
 * This service handles loading and validating configuration from YAML files,
 * creating the appropriate settings objects for each module.</p>
 *
 * @author  lye
 * @since   0.1
 */
public class ConfigLoaderService {

    private final TradeFlow plugin;

    // Loaded settings
    private IPluginSettings pluginSettings;
    private IPricingSettings pricingSettings;
    private IGuiSettings guiSettings;
    private IMessageSettings messageSettings;
    private IShopDefinitions shopDefinitions;
    private IAutosellSettings autosellSettings;
    private IEconomicEventSettings economicEventSettings;
    private ITaxSettings taxSettings;

    /**
     * Creates a new configuration loader service.
     *
     * @param plugin the plugin instance
     */
    public ConfigLoaderService(TradeFlow plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads all configuration modules.
     *
     * @throws com.github.lye.error.ConfigurationException if configuration is invalid
     */
    public void loadAll() {
        plugin.getTradeLogger().config("Loading configuration modules...");

        // Initialize Config if not already done
        Config.init(plugin, plugin.getTradeLogger());

        Config loadedConfig = Config.get();
        if (loadedConfig == null) {
            throw new IllegalStateException("Config initialization returned null instance");
        }

        // Reuse normalized settings created in Config to avoid divergence and null modules.
        this.pluginSettings = loadedConfig.getPluginSettings();
        this.pricingSettings = loadedConfig.getPricingSettings();
        this.guiSettings = loadedConfig.getGuiSettings();
        this.messageSettings = loadedConfig.getMessageSettings();
        this.shopDefinitions = loadedConfig.getShopDefinitions();
        this.autosellSettings = loadedConfig.getAutosellSettings();
        this.economicEventSettings = loadedConfig.getEconomicEventSettings();

        this.taxSettings = new com.github.lye.config.settings.impl.DefaultTaxSettings(
            Config.getPricingModule(),
            plugin.getTradeLogger()
        );

        // Validate all settings
        validate();

        plugin.getTradeLogger().config("Configuration loaded successfully");
    }

    /**
     * Validates all loaded configuration.
     *
     * @throws com.github.lye.error.ConfigurationException if validation fails
     */
    private void validate() {
        com.github.lye.validation.ConfigValidator.validateAll(
            pluginSettings,
            pricingSettings,
            economicEventSettings,
            guiSettings,
            messageSettings,
            shopDefinitions
        );
    }

    /**
     * Reloads all configuration modules.
     *
     * @return true if reload was successful
     */
    public boolean reload() {
        try {
            // Reload all settings
            loadAll();
            return true;
        } catch (Exception e) {
            plugin.getTradeLogger().severe("Failed to reload configuration: " + e.getMessage());
            return false;
        }
    }

    // ==================== GETTERS ====================

    public IPluginSettings getPluginSettings() {
        return pluginSettings;
    }

    public IPricingSettings getPricingSettings() {
        return pricingSettings;
    }

    public IGuiSettings getGuiSettings() {
        return guiSettings;
    }

    public IMessageSettings getMessageSettings() {
        return messageSettings;
    }

    public IShopDefinitions getShopDefinitions() {
        return shopDefinitions;
    }

    public IAutosellSettings getAutosellSettings() {
        return autosellSettings;
    }

    public IEconomicEventSettings getEconomicEventSettings() {
        return economicEventSettings;
    }

    public ITaxSettings getTaxSettings() {
        return taxSettings;
    }
}
