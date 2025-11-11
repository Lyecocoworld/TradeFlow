package com.github.lye.events;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.Bukkit;
import com.github.lye.TradeFlow;
import com.github.lye.config.EventsConfig;
import com.github.lye.database.ServerStateData;
import com.github.lye.util.Format;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class EconomicEventManager {

    private static final String KEY_ACTIVE_EVENT = "active_event_name";
    private static final String KEY_EVENT_END_TS = "active_event_end_timestamp";
    private static final String KEY_NEXT_EVENT_TS = "next_event_start_timestamp";

    private final TradeFlow plugin;
    private final ServerStateData serverStateData; // Can be null
    private final List<EconomicEvent> possibleEvents = new ArrayList<>();
    private final Random random = new Random();

    // In-memory state for non-database mode
    private EconomicEvent activeEvent = null;
    private int timeToNextEvent;

    public EconomicEventManager(TradeFlow plugin, ServerStateData serverStateData) {
        this.plugin = plugin;
        this.serverStateData = serverStateData;
        loadEvents();

        if (serverStateData == null) {
            // Schedule first event for non-db mode
            scheduleNextEventLocally();
        }
    }

    private void loadEvents() {
        ConfigurationSection eventsSection = EventsConfig.get().getEconomicEvents();
        if (eventsSection == null) {
            plugin.getLogger().warning("Could not find 'economic-events' section in config file.");
            return;
        }

        for (String key : eventsSection.getKeys(false)) {
            ConfigurationSection eventConfig = eventsSection.getConfigurationSection(key);
            if (eventConfig != null) {
                possibleEvents.add(new EconomicEvent(eventConfig));
            }
        }
        plugin.getLogger().info("Loaded " + possibleEvents.size() + " economic events.");
    }

    public void tick() {
        if (serverStateData != null) {
            tickDatabaseMode();
        } else {
            tickLocalMode();
        }
    }

    private void tickLocalMode() {
        if (activeEvent != null) {
            activeEvent.tick();
            if (activeEvent.isFinished()) {
                Bukkit.broadcast(MiniMessage.miniMessage().deserialize(activeEvent.getEndMessage()));
                activeEvent = null;
                scheduleNextEventLocally();
            }
        } else {
            timeToNextEvent--;
            if (timeToNextEvent <= 0) {
                startRandomEventLocally();
            }
        }
    }

    private void tickDatabaseMode() {
        long currentTime = System.currentTimeMillis();
        String activeEventName = serverStateData.getState(KEY_ACTIVE_EVENT);
        String endTimeStr = serverStateData.getState(KEY_EVENT_END_TS);
        long endTime = endTimeStr == null ? 0 : Long.parseLong(endTimeStr);

        // Check if an event is currently active and should end
        if (activeEventName != null && !activeEventName.isEmpty()) {
            if (currentTime >= endTime) {
                EconomicEvent event = findEventByName(activeEventName);
                if (event != null) {
                    Bukkit.broadcast(MiniMessage.miniMessage().deserialize(event.getEndMessage()));
                }
                serverStateData.setState(KEY_ACTIVE_EVENT, "");
                serverStateData.setState(KEY_EVENT_END_TS, "0");
                // Schedule the next event
                long nextEventTime = currentTime + ThreadLocalRandom.current().nextLong(3600000, 7200001);
                serverStateData.setState(KEY_NEXT_EVENT_TS, String.valueOf(nextEventTime));
            }
        } else {
            // Check if a new event should start
            String nextTimeStr = serverStateData.getState(KEY_NEXT_EVENT_TS);
            long nextTime = nextTimeStr == null ? 0 : Long.parseLong(nextTimeStr);

            if (nextTime == 0) { // First time ever, schedule one
                long nextEventTime = currentTime + ThreadLocalRandom.current().nextLong(3600000, 7200001);
                serverStateData.setState(KEY_NEXT_EVENT_TS, String.valueOf(nextEventTime));
            } else if (currentTime >= nextTime) {
                startRandomEventDatabase();
            }
        }
    }

    private void scheduleNextEventLocally() {
        this.timeToNextEvent = ThreadLocalRandom.current().nextInt(3600, 7201); // 1-2 hours in seconds
    }

    private void startRandomEventLocally() {
        if (possibleEvents.isEmpty()) return;
        this.activeEvent = possibleEvents.get(random.nextInt(possibleEvents.size()));
        this.activeEvent.start();
        Bukkit.broadcast(MiniMessage.miniMessage().deserialize(activeEvent.getStartMessage()));
    }

    private void startRandomEventDatabase() {
        if (possibleEvents.isEmpty()) return;
        EconomicEvent eventToStart = possibleEvents.get(random.nextInt(possibleEvents.size()));
        long endTime = System.currentTimeMillis() + (eventToStart.getDuration() * 50); // duration is in ticks

        serverStateData.setState(KEY_ACTIVE_EVENT, eventToStart.getName());
        serverStateData.setState(KEY_EVENT_END_TS, String.valueOf(endTime));
        serverStateData.setState(KEY_NEXT_EVENT_TS, "0"); // Clear the next event trigger

        Bukkit.broadcast(MiniMessage.miniMessage().deserialize(eventToStart.getStartMessage()));
    }

    public EconomicEvent getActiveEvent() {
        if (serverStateData != null) {
            String activeEventName = serverStateData.getState(KEY_ACTIVE_EVENT);
            return findEventByName(activeEventName);
        } else {
            return activeEvent;
        }
    }

    private EconomicEvent findEventByName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return possibleEvents.stream()
                .filter(event -> event.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }
}