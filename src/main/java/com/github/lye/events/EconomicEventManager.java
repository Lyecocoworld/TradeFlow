package com.github.lye.events;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.Bukkit;
import com.github.lye.TradeFlow;
import com.github.lye.config.EventsConfig;
import com.github.lye.repository.ServerStateRepository;
import com.github.lye.redis.RedisClient;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.github.lye.config.settings.IEconomicEventSettings;
import com.github.lye.config.settings.IPricingSettings;

public class EconomicEventManager {

    private static final String KEY_ACTIVE_EVENT = "active_event_name";
    private static final String KEY_EVENT_END_TS = "active_event_end_timestamp";
    private static final String KEY_NEXT_EVENT_TS = "next_event_start_timestamp";
    private static final String EVENT_CHANNEL = "tradeflow:event-updates";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final TradeFlow plugin;
    private final ServerStateRepository serverStateRepository; // Can be null
    private final IEconomicEventSettings economicEventSettings;
    private final IPricingSettings pricingSettings;
    private final List<EconomicEvent> possibleEvents = new ArrayList<>();
    private final Random random = new Random();
    private final RedisClient redisClient;
    private final String serverId;

    // In-memory state for non-database mode
    private EconomicEvent activeEvent = null;
    private int timeToNextEvent;
    private long localEndTime;
    private net.kyori.adventure.bossbar.BossBar bossBar;

    public EconomicEventManager(TradeFlow plugin,
                                ServerStateRepository serverStateRepository,
                                IEconomicEventSettings economicEventSettings,
                                IPricingSettings pricingSettings,
                                RedisClient redisClient) {
        this.plugin = plugin;
        this.serverStateRepository = serverStateRepository;
        this.economicEventSettings = economicEventSettings;
        this.pricingSettings = pricingSettings;
        this.redisClient = redisClient;
        this.serverId = plugin.getPluginSettings().getRedisServerId();
        loadEvents();

        if (serverStateRepository == null) {
            // Schedule first event for non-db mode
            scheduleNextEventLocally();
        }
    }

    private void updateBossBar(EconomicEvent event, long endTime) {
        if (bossBar == null) {
            bossBar = net.kyori.adventure.bossbar.BossBar.bossBar(
                    net.kyori.adventure.text.Component.empty(),
                    1.0f,
                    net.kyori.adventure.bossbar.BossBar.Color.GREEN,
                    net.kyori.adventure.bossbar.BossBar.Overlay.PROGRESS
            );
        }

        long timeLeft = endTime - System.currentTimeMillis();
        if (timeLeft <= 0) {
            hideBossBar();
            return;
        }
        
        float progress = (float) timeLeft / (event.getDuration() * 50f); // Duration is ticks * 50ms
        progress = Math.max(0.0f, Math.min(1.0f, progress));
        
        bossBar.name(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(event.getDisplay() + " <gray>(" + (timeLeft / 1000) + "s)"));
        bossBar.progress(progress);
        
        // Show to all players
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(bossBar);
        }
    }
    
    private void hideBossBar() {
        if (bossBar != null) {
            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bossBar);
            }
            bossBar = null;
        }
    }

    private void loadEvents() {
        ConfigurationSection eventsSection = economicEventSettings.getEconomicEvents();
        if (eventsSection == null) {
            plugin.getLogger().warning("Could not find 'economic-events' section in config file.");
            return;
        }

            // plugin.getLogger().info("Keys found in economic events section: " + eventsSection.getKeys(false).toString());
        for (String key : eventsSection.getKeys(false)) {
            ConfigurationSection eventConfig = eventsSection.getConfigurationSection(key);
            if (eventConfig != null) {
                possibleEvents.add(new EconomicEvent(eventConfig));
            }
        }
        // plugin.getLogger().info("Loaded " + possibleEvents.size() + " economic events.");
    }

    public void tick() {
        if (serverStateRepository != null) {
            tickDatabaseMode();
        } else {
            tickLocalMode();
        }
    }

    private void tickLocalMode() {
        if (activeEvent != null) {
            activeEvent.tick();
            
            // Visual update
            updateBossBar(activeEvent, localEndTime);
            
            if (activeEvent.isFinished()) {
                hideBossBar();
                Bukkit.broadcast(MiniMessage.miniMessage().deserialize(activeEvent.getEndMessage()));
                activeEvent = null;
                scheduleNextEventLocally();
            }
        } else {
            hideBossBar();
            timeToNextEvent--;
            if (timeToNextEvent <= 0) {
                // If a manual event was started, timeToNextEvent would be -1.
                // Reset it for normal scheduling only if no event is active.
                if (activeEvent == null) {
                    startRandomEconomicEvent();
                }
            }
        }
    }

    private void tickDatabaseMode() {
        long currentTime = System.currentTimeMillis();
        String activeEventName = serverStateRepository.getState(KEY_ACTIVE_EVENT);
        String endTimeStr = serverStateRepository.getState(KEY_EVENT_END_TS);
        long endTime = endTimeStr == null ? 0 : Long.parseLong(endTimeStr);

        // Check if an event is currently active and should end
        if (activeEventName != null && !activeEventName.isEmpty()) {
            EconomicEvent event = findEventByName(activeEventName);
            if (event != null) {
                updateBossBar(event, endTime);
            }
            
            if (currentTime >= endTime) {
                hideBossBar();
                if (event != null) {
                    Bukkit.broadcast(MiniMessage.miniMessage().deserialize(event.getEndMessage()));
                }
                serverStateRepository.setState(KEY_ACTIVE_EVENT, "");
                serverStateRepository.setState(KEY_EVENT_END_TS, "0");
                publishEventUpdate(activeEventName, "ended");
                // Schedule the next event
                long nextEventTime = currentTime + ThreadLocalRandom.current().nextLong(pricingSettings.getEventMinIntervalMs(), pricingSettings.getEventMaxIntervalMs() + 1);
                serverStateRepository.setState(KEY_NEXT_EVENT_TS, String.valueOf(nextEventTime));
            }
        } else {
            hideBossBar();
            // Check if a new event should start
            String nextTimeStr = serverStateRepository.getState(KEY_NEXT_EVENT_TS);
            long nextTime = nextTimeStr == null ? 0 : Long.parseLong(nextTimeStr);

            if (nextTime == 0) { // First time ever, schedule one
                long nextEventTime = currentTime + ThreadLocalRandom.current().nextLong(pricingSettings.getEventMinIntervalMs(), pricingSettings.getEventMaxIntervalMs() + 1);
                serverStateRepository.setState(KEY_NEXT_EVENT_TS, String.valueOf(nextEventTime));
            } else if (currentTime >= nextTime) {
                startRandomEconomicEvent();
            }
        }
    }

    private void scheduleNextEventLocally() {
        int minSec = (int) (pricingSettings.getEventMinIntervalMs() / 1000);
        int maxSec = (int) (pricingSettings.getEventMaxIntervalMs() / 1000) + 1;
        this.timeToNextEvent = ThreadLocalRandom.current().nextInt(minSec, maxSec); // Configurable interval in seconds
    }

    private boolean startEconomicEventLocally(EconomicEvent eventToStart) {
        // End any currently active local event first (if a new one is being forced)
        if (activeEvent != null && activeEvent != eventToStart) { // Only end if different event is starting
            Bukkit.broadcast(MiniMessage.miniMessage().deserialize(activeEvent.getEndMessage()));
        }
        this.activeEvent = eventToStart;
        this.activeEvent.start();
        this.localEndTime = System.currentTimeMillis() + (eventToStart.getDuration() * 50L);
        Bukkit.broadcast(MiniMessage.miniMessage().deserialize(activeEvent.getStartMessage()));
        this.timeToNextEvent = -1; // Force immediate tick for next event processing if desired, or set to a small value
        return true;
    }

    private boolean startEconomicEventDatabase(EconomicEvent eventToStart) {
        long currentTime = System.currentTimeMillis();
        long endTime = currentTime + (eventToStart.getDuration() * 50); // duration is in ticks

        // Check if an event is currently active and end it before starting a new one
        String currentActiveEventName = serverStateRepository.getState(KEY_ACTIVE_EVENT);
        if (currentActiveEventName != null && !currentActiveEventName.isEmpty() && !currentActiveEventName.equalsIgnoreCase(eventToStart.getName())) {
            EconomicEvent currentlyActiveEvent = findEventByName(currentActiveEventName);
            if (currentlyActiveEvent != null) {
                Bukkit.broadcast(MiniMessage.miniMessage().deserialize(currentlyActiveEvent.getEndMessage()));
            }
            serverStateRepository.setState(KEY_ACTIVE_EVENT, "");
            serverStateRepository.setState(KEY_EVENT_END_TS, "0");
            publishEventUpdate(currentActiveEventName, "ended");
            hideBossBar(); // Ensure UI is cleared
        }

        serverStateRepository.setState(KEY_ACTIVE_EVENT, eventToStart.getName());
        serverStateRepository.setState(KEY_EVENT_END_TS, String.valueOf(endTime));
        serverStateRepository.setState(KEY_NEXT_EVENT_TS, "0"); // Clear the next event trigger

        Bukkit.broadcast(MiniMessage.miniMessage().deserialize(eventToStart.getStartMessage()));
        publishEventUpdate(eventToStart.getName(), "started");
        return true;
    }

    public boolean startRandomEconomicEvent() {
        if (possibleEvents.isEmpty()) {
            plugin.getLogger().warning("No economic events configured to start.");
            return false;
        }

        EconomicEvent eventToStart = possibleEvents.get(random.nextInt(possibleEvents.size()));
        return startEconomicEvent(eventToStart);
    }

    public boolean startSpecificEconomicEvent(String eventName) {
        if (possibleEvents.isEmpty()) {
            plugin.getLogger().warning("No economic events configured to start.");
            return false;
        }

        EconomicEvent eventToStart = findEventByName(eventName);
        if (eventToStart == null) {
            plugin.getLogger().warning("Economic event '" + eventName + "' not found.");
            return false;
        }
        return startEconomicEvent(eventToStart);
    }

    private boolean startEconomicEvent(EconomicEvent eventToStart) {
        // Clear scheduled next event as one is starting now
        if (serverStateRepository != null) {
            serverStateRepository.setState(KEY_NEXT_EVENT_TS, "0");
        } else {
            this.timeToNextEvent = -1; // Indicate no scheduled next event for local mode
        }

        if (serverStateRepository != null) {
            return startEconomicEventDatabase(eventToStart);
        } else {
            return startEconomicEventLocally(eventToStart);
        }
    }

    public EconomicEvent getActiveEvent() {
        if (serverStateRepository != null) {
            String activeEventName = serverStateRepository.getState(KEY_ACTIVE_EVENT);
            return findEventByName(activeEventName);
        } else {
            return activeEvent;
        }
    }

    public List<String> getPossibleEventNames() {
        return possibleEvents.stream()
                .map(EconomicEvent::getName)
                .collect(Collectors.toList());
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

    private void publishEventUpdate(String eventName, String state) {
        if (redisClient == null || !redisClient.isEnabled()) {
            return;
        }
        try {
            EconomicEvent event = findEventByName(eventName);
            long endTimeTs = 0;
            int durationTicks = 0;
            String display = eventName;
            String startMsg = "";
            String endMsg = "";
            if (event != null) {
                durationTicks = event.getDuration();
                display = event.getDisplay();
                startMsg = event.getStartMessage();
                endMsg = event.getEndMessage();
                if ("started".equals(state)) {
                    endTimeTs = System.currentTimeMillis() + (durationTicks * 50L);
                }
            }

            com.github.lye.redis.messages.EventUpdateMessage msg =
                    new com.github.lye.redis.messages.EventUpdateMessage(
                            serverId, eventName, state, endTimeTs, durationTicks,
                            display, startMsg, endMsg);
            String payload = objectMapper.writeValueAsString(msg);
            redisClient.publish(EVENT_CHANNEL, payload);
        } catch (Exception e) {
            plugin.getLogger().warning("[Events] Failed to publish event update: " + e.getMessage());
        }
    }

    /**
     * Applies a remote event update received from another server via Redis pub/sub.
     * <p>
     * Skips messages originating from this server (deduplication by serverId).
     * When a remote "started" event arrives, cancels any local event and sets
     * the active event to the received one. When "ended" arrives, clears the
     * active event.
     *
     * @param message the deserialized event update message
     */
    public void applyRemoteEventUpdate(com.github.lye.redis.messages.EventUpdateMessage message) {
        // Deduplication: skip self-published messages
        if (serverId != null && serverId.equals(message.getServerId())) {
            return;
        }

        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            try {
                if ("started".equals(message.getState())) {
                    // Cancel any locally active event first
                    if (activeEvent != null) {
                        hideBossBar();
                    }

                    EconomicEvent event = findEventByName(message.getEventName());
                    if (event != null) {
                        this.activeEvent = event;
                        this.activeEvent.start();
                        this.localEndTime = message.getEndTimeTimestamp();
                        updateBossBar(activeEvent, localEndTime);
                        Bukkit.broadcast(MiniMessage.miniMessage().deserialize(event.getStartMessage()));
                        plugin.getLogger().info("[Events] Applied remote event: " + event.getName()
                                + " from server " + message.getServerId());
                    } else {
                        plugin.getLogger().warning("[Events] Remote event '" + message.getEventName()
                                + "' not found in local config, skipping.");
                    }
                } else if ("ended".equals(message.getState())) {
                    if (activeEvent != null) {
                        Bukkit.broadcast(MiniMessage.miniMessage().deserialize(activeEvent.getEndMessage()));
                    }
                    this.activeEvent = null;
                    hideBossBar();
                    scheduleNextEventLocally();
                    plugin.getLogger().info("[Events] Remote event ended"
                            + " from server " + message.getServerId());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Events] Error applying remote event: " + e.getMessage());
            }
        });
    }
}
