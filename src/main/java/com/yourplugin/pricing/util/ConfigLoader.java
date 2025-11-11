package com.yourplugin.pricing.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.yourplugin.pricing.model.GlobalPricingConfig;
import com.yourplugin.pricing.model.ItemConfig;
import com.yourplugin.pricing.service.PricingUtils;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public class ConfigLoader {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final ObjectMapper mapper;

    public ConfigLoader(JavaPlugin plugin, Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.logger = Objects.requireNonNull(logger, "Logger cannot be null");
        this.mapper = new ObjectMapper(new YAMLFactory());
        // For Java records, ParameterNamesModule is needed to map constructor parameters
        this.mapper.registerModule(new ParameterNamesModule());
        // For Optional types
        this.mapper.registerModule(new Jdk8Module());
        // Ignore unknown properties to allow for extra fields in config files
        this.mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public Map.Entry<Map<com.yourplugin.pricing.model.ItemId, ItemConfig>, GlobalPricingConfig> load(String fileName) {
        File configFile = new File(plugin.getDataFolder(), fileName);

        if (!configFile.exists()) {
            logger.warning(String.format("[AutoPricing] Le fichier %s n'existe pas. Copie du fichier par défaut.", fileName));
            plugin.saveResource(fileName, false);
            // If saveResource fails or file is still not there, return empty config
            if (!configFile.exists()) {
                logger.severe(String.format("[AutoPricing] Impossible de copier le fichier %s par défaut.", fileName));
                return Map.entry(Collections.emptyMap(), new GlobalPricingConfig());
            }
        }

        try {
            // Read the entire YAML file as a generic Map
            Map<String, Object> rawConfig = mapper.readValue(configFile, Map.class);

            // Extract global pricing config
            GlobalPricingConfig globalConfig = new GlobalPricingConfig(); // Default values
            if (rawConfig.containsKey("pricing")) {
                // Use convertValue to map the 'pricing' section to GlobalPricingConfig
                globalConfig = mapper.convertValue(rawConfig.get("pricing"), GlobalPricingConfig.class);
            }

            // Extract item configurations from the 'items' section
            Map<com.yourplugin.pricing.model.ItemId, ItemConfig> itemConfigs = new HashMap<>();
            if (rawConfig.containsKey("items")) {
                Map<String, Object> shopsSection = (Map<String, Object>) rawConfig.get("items");
                for (Map.Entry<String, Object> entry : shopsSection.entrySet()) {
                    String itemIdString = PricingUtils.normalize(entry.getKey());
                    com.yourplugin.pricing.model.ItemId itemId = new com.yourplugin.pricing.model.ItemId(itemIdString);

                    Map<String, Object> itemProps = (Map<String, Object>) entry.getValue();

                    String section = (String) itemProps.get("section");

                    Object priceObj = itemProps.get("price");
                    Double price = null;
                    if (priceObj instanceof Integer) {
                        price = ((Integer) priceObj).doubleValue();
                    } else if (priceObj instanceof Double) {
                        price = (Double) priceObj;
                    }

                    Integer maxBuy = (Integer) itemProps.get("max-buy");
                    Integer maxSell = (Integer) itemProps.get("max-sell");
                    Boolean free = (Boolean) itemProps.get("free");

                    // Extract PricingLocal parameters
                    Object volatilityObj = itemProps.get("volatility");
                    Double volatility = null;
                    if (volatilityObj instanceof Integer) {
                        volatility = ((Integer) volatilityObj).doubleValue();
                    } else if (volatilityObj instanceof Double) {
                        volatility = (Double) volatilityObj;
                    }

                    Object mObj = itemProps.get("m");
                    Double m = null;
                    if (mObj instanceof Integer) {
                        m = ((Integer) mObj).doubleValue();
                    } else if (mObj instanceof Double) {
                        m = (Double) mObj;
                    }

                    Object tObj = itemProps.get("t");
                    Double t = null;
                    if (tObj instanceof Integer) {
                        t = ((Integer) tObj).doubleValue();
                    } else if (tObj instanceof Double) {
                        t = (Double) tObj;
                    }

                    Object pminObj = itemProps.get("pmin");
                    Double pmin = null;
                    if (pminObj instanceof Integer) {
                        pmin = ((Integer) pminObj).doubleValue();
                    } else if (pminObj instanceof Double) {
                        pmin = (Double) pminObj;
                    }

                    Object pmaxObj = itemProps.get("pmax");
                    Double pmax = null;
                    if (pmaxObj instanceof Integer) {
                        pmax = ((Integer) pmaxObj).doubleValue();
                    } else if (pmaxObj instanceof Double) {
                        pmax = (Double) pmaxObj;
                    }

                    com.yourplugin.pricing.model.PricingLocal pricingLocal = new com.yourplugin.pricing.model.PricingLocal(m, t, pmin, pmax, volatility);

                    ItemConfig config = new ItemConfig(itemId, section, price, maxBuy, maxSell, free, pricingLocal);
                    itemConfigs.put(itemId, config);
                }
            }

            logger.info(String.format("[AutoPricing] Chargé %d configurations d'items depuis %s.", itemConfigs.size(), fileName));
            return Map.entry(itemConfigs, globalConfig);

        } catch (IOException e) {
            logger.severe(String.format("[AutoPricing] Erreur lors du chargement du fichier de configuration %s: %s", fileName, e.getMessage()));
            return Map.entry(Collections.emptyMap(), new GlobalPricingConfig());
        }
    }
}
