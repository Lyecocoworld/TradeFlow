package unprotesting.com.github.events;

import org.bukkit.configuration.ConfigurationSection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EconomicEvent {

    private final String name;
    private final String startMessage;
    private final String endMessage;
    private final int duration; // in ticks
    private final Set<String> affectedItems = new HashSet<>();
    private final double priceMultiplier;
    private int ticksRemaining;

    public EconomicEvent(ConfigurationSection config) {
        this.name = config.getName();
        this.startMessage = config.getString("start-message", "An economic event has started!");
        this.endMessage = config.getString("end-message", "The economic event has ended.");
        this.duration = config.getInt("duration-seconds", 300) * 20;
        this.priceMultiplier = config.getDouble("price-multiplier", 1.0);
        List<String> items = config.getStringList("affected-items");
        if (items != null) {
            for (String item : items) {
                affectedItems.add(item.toLowerCase());
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

    public boolean affectsItem(String itemName) {
        return affectedItems.contains(itemName.toLowerCase());
    }

    public String getName() {
        return name;
    }

    public String getStartMessage() {
        return startMessage;
    }

    public String getEndMessage() {
        return endMessage;
    }

    public double getPriceMultiplier() {
        return priceMultiplier;
    }

    public int getDuration() {
        return duration;
    }
}
