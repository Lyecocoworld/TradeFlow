package com.github.lye.events;

import java.util.List;
import java.util.ArrayList;
import org.bukkit.configuration.ConfigurationSection;

public class EventEffect {
    private final EventEffectType type;
    private final List<String> items;
    private final double value;

    public EventEffect(ConfigurationSection config) {
        this.type = EventEffectType.valueOf(config.getString("type", "PRICE_MULTIPLIER").toUpperCase());
        this.items = config.getStringList("items");
        this.value = config.getDouble("value", 1.0);
    }
    
    // Constructor for backward compatibility or simple creation
    public EventEffect(EventEffectType type, List<String> items, double value) {
        this.type = type;
        this.items = items != null ? items : new ArrayList<>();
        this.value = value;
    }

    public EventEffectType getType() { return type; }
    public List<String> getItems() { return items; }
    public double getValue() { return value; }
}
