package com.github.lye.events;

import org.bukkit.configuration.ConfigurationSection;
import java.util.ArrayList;
import java.util.List;

public class EconomicEvent {

    private final String name;
    private final String display;
    private final String startMessage;
    private final String endMessage;
    private final int duration; // in ticks
    private final List<EventEffect> effects = new ArrayList<>();
    private int ticksRemaining;

    public EconomicEvent(ConfigurationSection config) {
        this.name = config.getName();
        this.display = config.getString("display-name", name);
        this.startMessage = config.getString("start-message", "An economic event has started!");
        this.endMessage = config.getString("end-message", "The economic event has ended.");
        this.duration = config.getInt("duration-seconds", 300) * 20;

        // Support new "effects" list
        if (config.isList("effects")) {
            for (java.util.Map<?, ?> map : config.getMapList("effects")) {
                // Manual parsing of map list since ConfigurationSection is tricky with getMapList
                 // Or better, loop through keys if it's a section, but usually it's a list of maps
                 // Let's assume standard YAML list of objects. 
                 // Creating a dummy section or parsing manually.
                 // Ideally, we change the structure in YML to be sections or we handle maps.
            }
            // Using Bukkit's getMapList is raw. Let's use a safer approach:
            // We will read 'effects' as a generic list and try to parse.
            // Actually, for simplicity in this iteration, let's support the legacy format AND the new one.
            
        }
        
        // Parse NEW "effects" section if it exists as a list of maps
        List<java.util.Map<?, ?>> effectMaps = config.getMapList("effects");
        for (java.util.Map<?, ?> map : effectMaps) {
            try {
                EventEffectType type = EventEffectType.valueOf(((String) map.get("type")).toUpperCase());
                double value = map.containsKey("value") ? ((Number) map.get("value")).doubleValue() : 1.0;
                List<String> items = (List<String>) map.get("items");
                effects.add(new EventEffect(type, items, value));
            } catch (Exception e) {
                System.err.println("Failed to parse effect for event " + name + ": " + e.getMessage());
            }
        }

        // Backward compatibility: if no effects found, look for legacy fields
        if (effects.isEmpty()) {
            double multiplier = config.getDouble("price-multiplier", 1.0);
            if (multiplier != 1.0) {
                List<String> items = config.getStringList("affected-items");
                effects.add(new EventEffect(EventEffectType.PRICE_MULTIPLIER, items, multiplier));
            }
        }
    }

    public void start() {
        this.ticksRemaining = this.duration;
    }

    public void tick() {
        if (ticksRemaining > 0) {
            ticksRemaining--;
        }
    }

    public boolean isFinished() {
        return ticksRemaining <= 0;
    }

    public String getName() { return name; }
    public String getDisplay() { return display; }
    public String getStartMessage() { return startMessage; }
    public String getEndMessage() { return endMessage; }
    public int getDuration() { return duration; }
    public List<EventEffect> getEffects() { return effects; }
}
