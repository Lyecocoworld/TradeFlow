package com.github.lye.validation;

import com.github.lye.config.settings.IEconomicEventSettings;
import com.github.lye.config.settings.IGuiSettings;
import com.github.lye.config.settings.IMessageSettings;
import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.config.settings.IPricingSettings;
import com.github.lye.config.settings.IShopDefinitions;

/**
 * Validator for plugin configuration settings.
 * <p>
 * This class validates all configuration modules after loading to ensure
 * they contain valid values before the plugin starts using them.</p>
 *
 * @author  lye
 * @since   0.1
 */
public final class ConfigValidator {

    private ConfigValidator() {
        // Utility class - prevent instantiation
    }

    /**
     * Validates all plugin settings.
     *
     * @param pluginSettings the plugin settings to validate
     * @throws com.github.lye.error.ConfigurationException if validation fails
     */
    public static void validatePluginSettings(IPluginSettings pluginSettings) {
        ValidationResult.Collector collector = ValidationResult.collector();

        // Validate time period is positive
        collector.check(
            pluginSettings.getTimePeriod() > 0,
            "market.time-period must be positive"
        );

        ValidationResult result = collector.build();
        if (result.isInvalid()) {
            throw new com.github.lye.error.ConfigurationException(
                "Invalid plugin settings: " + String.join(", ", result.getErrors())
            );
        }
    }

    /**
     * Validates pricing settings.
     *
     * @param pricingSettings the pricing settings to validate
     * @throws com.github.lye.error.ConfigurationException if validation fails
     */
    public static void validatePricingSettings(IPricingSettings pricingSettings) {
        ValidationResult.Collector collector = ValidationResult.collector();

        // Validate start price is positive
        collector.check(
            pricingSettings.getStartPrice() > 0,
            "pricing.start-price must be positive"
        );

        // Validate volatility is in range [0, 1]
        double volatility = pricingSettings.getVolatility();
        collector.check(
            volatility >= 0.0 && volatility <= 1.0,
            "pricing.volatility must be between 0 and 1"
        );

        ValidationResult result = collector.build();
        if (result.isInvalid()) {
            throw com.github.lye.error.ConfigurationException.forKey("pricing",
                String.join("; ", result.getErrors())
            );
        }
    }

    /**
     * Validates economic event settings.
     *
     * @param eventSettings the economic event settings to validate
     * @throws com.github.lye.error.ConfigurationException if validation fails
     */
    public static void validateEconomicEventSettings(IEconomicEventSettings eventSettings) {
        // Basic validation - just check the section exists
        if (eventSettings.getEconomicEvents() == null) {
            throw com.github.lye.error.ConfigurationException.forKey("events",
                "Economic events configuration is missing"
            );
        }
    }

    /**
     * Validates GUI settings.
     *
     * @param guiSettings the GUI settings to validate
     * @throws com.github.lye.error.ConfigurationException if validation fails
     */
    public static void validateGuiSettings(IGuiSettings guiSettings) {
        ValidationResult.Collector collector = ValidationResult.collector();

        // Validate background is set
        collector.check(
            guiSettings.getBackground() != null,
            "gui.background must be set"
        );

        ValidationResult result = collector.build();
        if (result.isInvalid()) {
            throw com.github.lye.error.ConfigurationException.forKey("gui",
                String.join("; ", result.getErrors())
            );
        }
    }

    /**
     * Validates message settings.
     *
     * @param messageSettings the message settings to validate
     * @throws com.github.lye.error.ConfigurationException if validation fails
     */
    public static void validateMessageSettings(IMessageSettings messageSettings) {
        // Message settings are loaded from Config, so basic validation
        // The actual validation happens during Config loading
    }

    /**
     * Validates shop definitions.
     *
     * @param shopDefinitions the shop definitions to validate
     * @throws com.github.lye.error.ConfigurationException if validation fails
     */
    public static void validateShopDefinitions(IShopDefinitions shopDefinitions) {
        ValidationResult.Collector collector = ValidationResult.collector();

        // Validate sections exist
        collector.check(
            shopDefinitions.getSections() != null,
            "Shop sections configuration is missing"
        );

        collector.check(
            shopDefinitions.getItems() != null,
            "Shop items configuration is missing"
        );

        ValidationResult result = collector.build();
        if (result.isInvalid()) {
            throw com.github.lye.error.ConfigurationException.forKey("shops",
                String.join("; ", result.getErrors())
            );
        }
    }

    /**
     * Validates all configuration modules.
     *
     * @param pluginSettings      the plugin settings
     * @param pricingSettings     the pricing settings
     * @param eventSettings       the economic event settings
     * @param guiSettings         the GUI settings
     * @param messageSettings     the message settings
     * @param shopDefinitions     the shop definitions
     * @throws com.github.lye.error.ConfigurationException if any validation fails
     */
    public static void validateAll(IPluginSettings pluginSettings,
                                   IPricingSettings pricingSettings,
                                   IEconomicEventSettings eventSettings,
                                   IGuiSettings guiSettings,
                                   IMessageSettings messageSettings,
                                   IShopDefinitions shopDefinitions) {
        validatePluginSettings(pluginSettings);
        validatePricingSettings(pricingSettings);
        validateEconomicEventSettings(eventSettings);
        validateGuiSettings(guiSettings);
        validateMessageSettings(messageSettings);
        validateShopDefinitions(shopDefinitions);
    }
}
