package com.github.lye.config.settings;

import org.bukkit.configuration.ConfigurationSection;

public interface IShopDefinitions {
    ConfigurationSection getSections();
    ConfigurationSection getItems();
    ConfigurationSection getFamilies();
    ConfigurationSection getShops(); // legacy accessor (deprecated)
}
