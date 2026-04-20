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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
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
    private final ServerStateRepository serverStateRepository;
    private final IEconomicEventSettings economicEventSettings;
    private final IPricingSettings pricingSettings;
    private final List<EconomicEvent> possibleEvents = new ArrayList<>();
    private final Random random = new Random();
    private final RedisClient redisClient;
    private final String serverId;

    private EconomicEvent activeEvent = null;
    private int timeToNextEvent;
    private long localEndTime;
    private net.kyori.adventure.bossbar.BossBar bossBar;

    private final AtomicReference<String> cachedActiveEvent = new AtomicReference<>(null);
    private final AtomicReference<Long> cachedEndTimestamp = new AtomicReference<>(0L);
    private final AtomicReference<Long> cachedNextEventTs = new AtomicReference<>(0L);

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
        this.serverId = plugin.getServices().get(com.github.lye.config.settings.IPluginSettings.class).getRedisServerId();
        loadEvents();

        if (serverStateRepository == null) {
            scheduleNextEventLocally();
        } else {
            refreshStateCache();
        }
    }

    private void refreshStateCache() {
        if (serverStateRepository == null) return;

        CompletableFuture<String> activeFut = serverStateRepository.getState(KEY_ACTIVE_EVENT);
        CompletableFuture<String> endFut = serverStateRepository.getState(KEY_EVENT_END_TS);
        CompletableFuture<String> nextFut = serverStateRepository.getState(KEY_NEXT_EVENT_TS);

        CompletableFuture.allOf(activeFut, endFut, nextFut)
                .thenRun(() -> {
                    try {
                        cachedActiveEvent.set(activeFut.join());
                        String endStr = endFut.join();
                        cachedEndTimestamp.set(endStr != null ? Long.parseLong(endStr) : 0L);
                        String nextStr = nextFut.join();
                        cachedNextEventTs.set(nextStr != null ? Long.parseLong(nextStr) : 0L);
                    } catch (Exception e) {
                        plugin.getLogger().warning("[Events] Failed to refresh state cache: " + e.getMessage());
                    }
                })
                .exceptionally(ex -> {
                    plugin.getLogger().warning("[Events] State cache refresh failed: " + ex.getMessage());
                    return null;
                });
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
        
        float progress = (float) timeLeft / (event.getDuration() * 50f);
        progress = Math.max(0.0f, Math.min(1.0f, progress));
        
        bossBar.name(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(event.getDisplay() + " <gray>(" + (timeLeft / 1000) + "s)"));
        bossBar.progress(progress);
        
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

        for (String key : eventsSection.getKeys(false)) {
            ConfigurationSection eventConfig = eventsSection.getConfigurationSection(key);
            if (eventConfig != null) {
                possibleEvents.add(new EconomicEvent(eventConfig));
            }
        }
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
                if (activeEvent == null) {
                    startRandomEconomicEvent();
                }
            }
        }
    }

    private void tickDatabaseMode() {
        long currentTime = System.currentTimeMillis();
        String activeEventName = cachedActiveEvent.get();
        long endTime = cachedEndTimestamp.get();

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
                cachedActiveEvent.set(null);
                cachedEndTimestamp.set(0L);
                serverStateRepository.setState(KEY_ACTIVE_EVENT, "");
                serverStateRepository.setState(KEY_EVENT_END_TS, "0");
                publishEventUpdate(activeEventName, "ended");
                long nextEventTime = currentTime + ThreadLocalRandom.current().nextLong(pricingSettings.getEventMinIntervalMs(), pricingSettings.getEventMaxIntervalMs() + 1);
                cachedNextEventTs.set(nextEventTime);
                serverStateRepository.setState(KEY_NEXT_EVENT_TS, String.valueOf(nextEventTime));
            }
        } else {
            hideBossBar();
            long nextTime = cachedNextEventTs.get();

            if (nextTime == 0) {
                long nextEventTime = currentTime + ThreadLocalRandom.current().nextLong(pricingSettings.getEventMinIntervalMs(), pricingSettings.getEventMaxIntervalMs() + 1);
                cachedNextEventTs.set(nextEventTime);
                serverStateRepository.setState(KEY_NEXT_EVENT_TS, String.valueOf(nextEventTime));
            } else if (currentTime >= nextTime) {
                startRandomEconomicEvent();
            }
        }

        refreshStateCache();
    }

    private void scheduleNextEventLocally() {
        int minSec = (int) (pricingSettings.getEventMinIntervalMs() / 1000);
        int maxSec = (int) (pricingSettings.getEventMaxIntervalMs() / 1000) + 1;
        this.timeToNextEvent = ThreadLocalRandom.current().nextInt(minSec, maxSec);
    }

    private boolean startEconomicEventLocally(EconomicEvent eventToStart) {
        if (activeEvent != null && activeEvent != eventToStart) {
            Bukkit.broadcast(MiniMessage.miniMessage().deserialize(activeEvent.getEndMessage()));
        }
        this.activeEvent = eventToStart;
        this.activeEvent.start();
        this.localEndTime = System.currentTimeMillis() + (eventToStart.getDuration() * 50L);
        Bukkit.broadcast(MiniMessage.miniMessage().deserialize(activeEvent.getStartMessage()));
        this.timeToNextEvent = -1;
        return true;
    }

    private boolean startEconomicEventDatabase(EconomicEvent eventToStart) {
        long currentTime = System.currentTimeMillis();
        long endTime = currentTime + (eventToStart.getDuration() * 50);

        String currentActiveEventName = cachedActiveEvent.get();
        if (currentActiveEventName != null && !currentActiveEventName.isEmpty() && !currentActiveEventName.equalsIgnoreCase(eventToStart.getName())) {
            EconomicEvent currentlyActiveEvent = findEventByName(currentActiveEventName);
            if (currentlyActiveEvent != null) {
                Bukkit.broadcast(MiniMessage.miniMessage().deserialize(currentlyActiveEvent.getEndMessage()));
            }
            cachedActiveEvent.set(null);
            cachedEndTimestamp.set(0L);
            serverStateRepository.setState(KEY_ACTIVE_EVENT, "");
            serverStateRepository.setState(KEY_EVENT_END_TS, "0");
            publishEventUpdate(currentActiveEventName, "ended");
            hideBossBar();
        }

        cachedActiveEvent.set(eventToStart.getName());
        cachedEndTimestamp.set(endTime);
        cachedNextEventTs.set(0L);
        serverStateRepository.setState(KEY_ACTIVE_EVENT, eventToStart.getName());
        serverStateRepository.setState(KEY_EVENT_END_TS, String.valueOf(endTime));
        serverStateRepository.setState(KEY_NEXT_EVENT_TS, "0");

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

    public boolean stopCurrentEvent() {
        if (serverStateRepository != null) {
            String activeEventName = cachedActiveEvent.get();
            if (activeEventName == null || activeEventName.isEmpty()) {
                return false;
            }
            EconomicEvent event = findEventByName(activeEventName);
            hideBossBar();
            if (event != null) {
                Bukkit.broadcast(MiniMessage.miniMessage().deserialize(event.getEndMessage()));
            }
            cachedActiveEvent.set(null);
            cachedEndTimestamp.set(0L);
            serverStateRepository.setState(KEY_ACTIVE_EVENT, "");
            serverStateRepository.setState(KEY_EVENT_END_TS, "0");
            publishEventUpdate(activeEventName, "ended");
            return true;
        } else {
            if (activeEvent == null) {
                return false;
            }
            hideBossBar();
            Bukkit.broadcast(MiniMessage.miniMessage().deserialize(activeEvent.getEndMessage()));
            activeEvent = null;
            scheduleNextEventLocally();
            return true;
        }
    }

    private boolean startEconomicEvent(EconomicEvent eventToStart) {
        if (serverStateRepository != null) {
            cachedNextEventTs.set(0L);
            serverStateRepository.setState(KEY_NEXT_EVENT_TS, "0");
        } else {
            this.timeToNextEvent = -1;
        }

        if (serverStateRepository != null) {
            return startEconomicEventDatabase(eventToStart);
        } else {
            return startEconomicEventLocally(eventToStart);
        }
    }

    public EconomicEvent getActiveEvent() {
        if (serverStateRepository != null) {
            String activeEventName = cachedActiveEvent.get();
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

    public void applyRemoteEventUpdate(com.github.lye.redis.messages.EventUpdateMessage message) {
        if (serverId != null && serverId.equals(message.getServerId())) {
            return;
        }

        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            try {
                if ("started".equals(message.getState())) {
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
