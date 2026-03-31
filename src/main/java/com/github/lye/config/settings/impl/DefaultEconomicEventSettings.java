package com.github.lye.config.settings.impl;

import com.github.lye.config.settings.IEconomicEventSettings;
import com.github.lye.util.TradeFlowLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class DefaultEconomicEventSettings implements IEconomicEventSettings {

    private final ConfigurationSection economicEvents;
    private final TradeFlowLogger logger;

    public DefaultEconomicEventSettings(YamlConfiguration configYml, TradeFlowLogger logger, org.bukkit.plugin.Plugin plugin) {
        this.logger = logger;

        // Primary source: config.yml under "economic-events"
        ConfigurationSection section = configYml.getConfigurationSection("economic-events");

        if (section == null) {
            // Fallback: try to load economic-events.yml from plugin data folder
            if (plugin != null) {
                File eventsFile = new File(plugin.getDataFolder(), "modules/events/economic-events.yml");
                if (!eventsFile.exists()) {
                    try {
                        plugin.saveResource("modules/events/economic-events.yml", false);
                        logger.info("economic-events.yml not found; default modules/events/economic-events.yml has been copied to data folder.");
                    } catch (IllegalArgumentException ignored) {
                        // Resource not present in the jar
                    }
                }
                if (eventsFile.exists()) {
                    YamlConfiguration eventsYml = YamlConfiguration.loadConfiguration(eventsFile);
                    section = eventsYml.getConfigurationSection("economic-events");
                    if (section != null) {
                        // logger.info("Loaded economic events from modules/events/economic-events.yml.");
                    }
                }
            }
        } else {
            logger.finer("Loaded economic events configuration from config.yml.");
        }

        if (section == null) {
            logger.warning("Could not find 'economic-events' section in config.yml or economic-events.yml.");
        }

        this.economicEvents = section;
    }

    @Override
    public ConfigurationSection getEconomicEvents() {
        return economicEvents;
    }
}
