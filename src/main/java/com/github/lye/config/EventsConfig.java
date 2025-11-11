package com.github.lye.config;

import java.io.File;
import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import com.github.lye.TradeFlow;

@Getter
public class EventsConfig {

    private static EventsConfig instance;
    private File file;
    private YamlConfiguration config;

    private final ConfigurationSection economicEvents;

    public static void init() {
        instance = new EventsConfig();
    }

    public static EventsConfig get() {
        return instance;
    }

    private EventsConfig() {
        TradeFlow plugin = TradeFlow.getInstance();
        String filename = "economic-events.yml";
        this.file = new File(plugin.getDataFolder(), filename);

        if (!file.exists()) {
            plugin.saveResource(filename, false);
        }

        this.config = YamlConfiguration.loadConfiguration(file);
        this.economicEvents = config.getConfigurationSection("economic-events");
    }

    public ConfigurationSection getEconomicEvents() {
        return economicEvents;
    }
}
