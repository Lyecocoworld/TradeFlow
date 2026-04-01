package com.github.lye.config.settings.impl;

import com.github.lye.config.settings.IShopDefinitions;
import com.github.lye.util.Format;
import com.github.lye.util.TradeFlowLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public class DefaultShopDefinitions implements IShopDefinitions {

    private final ConfigurationSection sections;
    private final ConfigurationSection items;
    private final ConfigurationSection families;
    private final ConfigurationSection shops; // legacy (pre-items)
    private final TradeFlowLogger logger;

    public DefaultShopDefinitions(YamlConfiguration shopsYml, TradeFlowLogger logger) {
        this.logger = logger;

        this.sections = shopsYml.getConfigurationSection("sections");
        logger.finer("Loaded sections configuration.");
        this.items = shopsYml.getConfigurationSection("items");
        logger.finer("Loaded items configuration.");
        this.families = shopsYml.getConfigurationSection("families");
        logger.finer("Loaded families configuration.");
        this.shops = shopsYml.getConfigurationSection("shops");
        if (this.items == null && this.shops != null) {
            logger.warning("shops.yml uses legacy 'shops' block. Please migrate to 'items'. Using legacy block for now.");
        }
    }

    @Override
    public ConfigurationSection getSections() {
        return sections;
    }

    @Override
    public ConfigurationSection getItems() {
        return items;
    }

    @Override
    public ConfigurationSection getFamilies() {
        return families;
    }

    @Override
    public ConfigurationSection getShops() {
        return shops;
    }
}
