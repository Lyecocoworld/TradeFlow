package com.github.lye.config;

import com.github.lye.config.settings.IEconomicEventSettings;
import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;

@Getter
public class EventsConfig {

    private final IEconomicEventSettings economicEventSettings;

    public EventsConfig(IEconomicEventSettings economicEventSettings) {
        this.economicEventSettings = economicEventSettings;
    }

    public ConfigurationSection getEconomicEvents() {
        return economicEventSettings.getEconomicEvents();
    }
}
