package com.github.lye.config.settings;

import org.bukkit.configuration.ConfigurationSection;

public interface IAutosellSettings {
    ConfigurationSection getAutosell();
    void setAutosell(ConfigurationSection section);
}
