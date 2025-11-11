package com.yourplugin.pricing.config;

import com.yourplugin.pricing.model.Family;
import com.yourplugin.pricing.model.ItemId;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class FamiliesConfigLoader {

    private static final Logger LOGGER = Logger.getLogger(FamiliesConfigLoader.class.getName());

    public static List<Family> loadFamilies(YamlConfiguration config) {
        List<Family> families = new ArrayList<>();
        ConfigurationSection familiesSection = config.getConfigurationSection("families");

        if (familiesSection == null) {
            LOGGER.info("No 'families' section found in configuration.");
            return families;
        }

        for (String rootKey : familiesSection.getKeys(false)) {
            ItemId rootItem = new ItemId(rootKey);
            ConfigurationSection familyData = familiesSection.getConfigurationSection(rootKey);

            if (familyData == null) {
                LOGGER.warning("Invalid family definition for root: " + rootKey + ". Skipping.");
                continue;
            }

            List<String> variantKeys = familyData.getStringList("variants");
            if (variantKeys.isEmpty()) {
                // Also check for single 'variant' key
                String singleVariant = familyData.getString("variant");
                if (singleVariant != null) {
                    variantKeys = List.of(singleVariant);
                } else {
                    LOGGER.warning("Family '" + rootKey + "' has no variants defined. Skipping.");
                    continue;
                }
            }

            List<ItemId> variantItems = variantKeys.stream()
                    .map(ItemId::new)
                    .collect(Collectors.toList());

            families.add(new Family(rootItem, variantItems));
        }
        LOGGER.info(String.format("Loaded %d families from configuration.", families.size()));
        return families;
    }
}
