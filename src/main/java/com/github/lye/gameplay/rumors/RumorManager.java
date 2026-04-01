package com.github.lye.gameplay.rumors;

import com.github.lye.TradeFlow;
import com.github.lye.util.Format;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RumorManager {

    private final TradeFlow plugin;
    private final NamespacedKey RUMOR_KEY;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private int globalRumorCount = 0;
    private YamlConfiguration config;
    private final BrokerReputation reputationManager;

    // Broker State
    private BrokerLocation currentBrokerLocation;
    private boolean isNight = false;
    private final List<FlashSale> currentFlashSales = new ArrayList<>();

    public RumorManager(TradeFlow plugin) {
        this.plugin = plugin;
        this.RUMOR_KEY = new NamespacedKey(plugin, "rumor_data");
        this.reputationManager = new BrokerReputation(plugin);
        reloadConfig();
    }

    public BrokerReputation getReputationManager() {
        return reputationManager;
    }

    public void tick() {
        // Check Main World Time (assuming 'world' or first world)
        World world = Bukkit.getWorlds().get(0);
        long time = world.getTime();
        boolean nowNight = time >= 13000 && time <= 23000;

        if (nowNight && !isNight) {
            // Sunset: Move Broker & Generate New Sales
            isNight = true;
            reloadConfig(); // Ensure config is fresh
            pickRandomLocation();
            generateFlashSales();
            if (currentBrokerLocation != null) {
                broadcastBrokerEmergence();
                plugin.getLogger().info("[RumorManager] Shadow Broker spawned at " + currentBrokerLocation.name);
            } else {
                plugin.getLogger().warning("[RumorManager] Failed to spawn Shadow Broker (no valid locations found).");
            }
        } else if (!nowNight && isNight) {
            // Sunrise: Hide Broker
            isNight = false;
            currentBrokerLocation = null;
            currentFlashSales.clear();
            Bukkit.broadcast(MiniMessage.miniMessage().deserialize("<gray>The Shadow Broker has vanished with the morning light.</gray>"));
        }

        // Particles effect if active
        if (isNight && currentBrokerLocation != null) {
            currentBrokerLocation.location.getWorld().spawnParticle(Particle.LARGE_SMOKE, currentBrokerLocation.location.clone().add(0.5, 1, 0.5), 5, 0.2, 0.5, 0.2, 0.0);
            if (System.currentTimeMillis() % 2000 < 50) { // Rarely play sound
                currentBrokerLocation.location.getWorld().playSound(currentBrokerLocation.location, Sound.ENTITY_VILLAGER_AMBIENT, 0.5f, 0.5f);
            }
        }
    }

    private void generateFlashSales() {
        currentFlashSales.clear();
        List<String> allItems = new ArrayList<>(plugin.getLoadedShops().keySet());
        if (allItems.isEmpty()) return;

        int itemCount = config.getInt("flash-sale.item-count", 3);
        double discountMin = config.getDouble("flash-sale.discount-min", 0.30);
        double discountMax = config.getDouble("flash-sale.discount-max", 0.60);
        int stockMin = config.getInt("flash-sale.stock-min", 16);
        int stockMax = config.getInt("flash-sale.stock-max", 64);

        // Generate random deals
        Random random = new Random();
        int count = Math.min(itemCount, allItems.size());
        for (int i = 0; i < count; i++) {
            String itemKey = allItems.get(random.nextInt(allItems.size()));
            com.github.lye.data.Shop shop = plugin.getLoadedShops().get(itemKey);
            if (shop == null) continue;

            // Discount: discountMin to discountMax off
            double discountRange = discountMax - discountMin;
            double discount = discountMin + (random.nextDouble() * discountRange);
            double price = shop.getPrice() * (1.0 - discount);
            int stockRange = Math.max(1, stockMax - stockMin);
            int stock = stockMin + random.nextInt(stockRange);

            currentFlashSales.add(new FlashSale(itemKey, price, stock, (int)(discount * 100)));
        }
    }

    public List<FlashSale> getFlashSales() {
        return currentFlashSales;
    }

    private void pickRandomLocation() {
        List<String> rawLocs = config.getStringList("broker-locations");
        if (rawLocs.isEmpty()) return;

        String raw = rawLocs.get(new Random().nextInt(rawLocs.size()));
        // Format: world, x, y, z, Name
        String[] parts = raw.split(",");
        if (parts.length < 5) return;

        try {
            World w = Bukkit.getWorld(parts[0].trim());
            if (w == null) return;
            double x = Double.parseDouble(parts[1].trim());
            double y = Double.parseDouble(parts[2].trim());
            double z = Double.parseDouble(parts[3].trim());
            String name = parts[4].trim();
            
            this.currentBrokerLocation = new BrokerLocation(new Location(w, x, y, z), name);
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid broker location: " + raw);
        }
    }

    public boolean canAccessBroker(Player player) {
        if (!isNight || currentBrokerLocation == null) {
            // Broker not spawned
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<dark_gray><b>⚠ Marché Noir</b></dark_gray>"
            ));
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<gray>Le Frateau n'apparaît que la nuit...</gray>"
            ));
            return false;
        }

        double interactionRadius = config.getDouble("rumors.broker-interaction-radius", 5.0);
        double distance = player.getLocation().distance(currentBrokerLocation.location);

        if (distance > interactionRadius) {
            // Hot/Cold system - give proximity hint
            BrokerReputation.ProximityLevel proximity = reputationManager.getProximityLevel(distance, interactionRadius * 10);

            player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<dark_gray><b>⚠ Marché Noir</b></dark_gray>"
            ));
            player.sendMessage(MiniMessage.miniMessage().deserialize(proximity.getMessage()));

            // Also show region hint if very far
            if (proximity == BrokerReputation.ProximityLevel.FAR || proximity == BrokerReputation.ProximityLevel.WARM) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<dark_gray>Indices: Recherche près de <gold>" + currentBrokerLocation.name + "</gold></dark_gray>"
                ));
            }
            return false;
        }

        return true;
    }

    public void reloadConfig() {
        File moduleDir = new File(plugin.getDataFolder(), "modules/rumors");
        if (!moduleDir.exists()) moduleDir.mkdirs();

        File file = new File(moduleDir, "rumors.yml");
        if (!file.exists()) {
            // saveResource extracts from src/main/resources to plugin data folder
            // We need to be careful with paths. saveResource("modules/rumors/rumors.yml", false) 
            // will extract to plugins/TradeFlow/modules/rumors/rumors.yml
            plugin.saveResource("modules/rumors/rumors.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public double getPrice(String tierId) {
        String path = "rumors.tiers." + tierId;
        if (!config.contains(path)) return -1.0;
        
        double basePrice = config.getDouble("rumors.base-price", 5000.0);
        double increase = config.getDouble("rumors.price-increase-per-buy", 500.0);
        double multiplier = config.getDouble(path + ".price-multiplier", 1.0);
        
        return (basePrice + (globalRumorCount * increase)) * multiplier;
    }

    public void purchaseRumor(Player player, String tierId) {
        if (tierId == null) tierId = "standard";
        String path = "rumors.tiers." + tierId;
        if (!config.contains(path)) {
            plugin.getMessageService().sendErrorMessage(player, "<red>Invalid rumor tier.</red>", null);
            return;
        }

        // 1. Cooldown Check
        long now = System.currentTimeMillis();
        long lastBuy = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        int cooldownSec = config.getInt("rumors.cooldown-seconds", 3600);
        if (now - lastBuy < cooldownSec * 1000L) {
            long remainingMin = ((lastBuy + cooldownSec * 1000L) - now) / 60000;
            String msg = config.getString("rumors.messages.cooldown", "<red>Wait <time>m.</red>")
                    .replace("<time>", String.valueOf(remainingMin));
            player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
            return;
        }

        // 2. Price Calculation
        double finalPrice = getPrice(tierId);

        if (plugin.getEconomy().getBalance(player) < finalPrice) {
            plugin.getMessageService().sendErrorMessage(player, "rumor-not-enough-money", 
                Placeholder.parsed("price", Format.currency(finalPrice)));
            return;
        }

        // 3. Get True Event Info
        String trueEventName = plugin.getServerStateData() != null 
                ? plugin.getServerStateData().getState("next_event_name") // Stored by EconomicEventManager
                : null;
        String trueTimeStr = plugin.getServerStateData() != null
                ? plugin.getServerStateData().getState("next_event_start_timestamp")
                : null;
        long trueTime = trueTimeStr != null ? Long.parseLong(trueTimeStr) : 0;

        // Fallback/Fake generation if no event scheduled
        if (trueEventName == null || trueTime <= now) {
             // Generate a completely fake "True" event for the sake of the mechanic, 
             // OR tell the player nothing is happening (boring).
             // Let's pick a random possible event as the "True" one for now to simulate simulation
             List<String> allEvents = plugin.getEconomicEventManager().getPossibleEventNames();
             if (!allEvents.isEmpty()) {
                 trueEventName = allEvents.get(new Random().nextInt(allEvents.size()));
                 trueTime = now + 3600000; // Fake 1h
             } else {
                 plugin.getMessageService().sendInfoMessage(player, "rumor-no-event", null);
                 return;
             }
        }

        // 4. Accuracy Check (Lie logic)
        double accuracy = config.getDouble(path + ".accuracy", 0.85);
        boolean isTruth = Math.random() < accuracy;
        String rumorsEventName = trueEventName;
        
        if (!isTruth) {
            // Pick a LIE (different event)
            List<String> allEvents = plugin.getEconomicEventManager().getPossibleEventNames();
            List<String> wrongEvents = new ArrayList<>(allEvents);
            wrongEvents.remove(trueEventName);
            if (!wrongEvents.isEmpty()) {
                rumorsEventName = wrongEvents.get(new Random().nextInt(wrongEvents.size()));
            }
        }

        // 5. Transaction
        plugin.getEconomy().withdrawPlayer(player, finalPrice);
        globalRumorCount++;
        cooldowns.put(player.getUniqueId(), now);

        // 6. Give Item
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        String tierName = config.getString(path + ".name", "Rumor");
        meta.displayName(MiniMessage.miniMessage().deserialize(tierName));
        
        List<Component> lore = new ArrayList<>();
        lore.add(MiniMessage.miniMessage().deserialize("<gray>Right-click to reveal.</gray>"));
        lore.add(MiniMessage.miniMessage().deserialize("<dark_gray>Tier: " + tierId.toUpperCase() + "</dark_gray>"));
        meta.lore(lore);

        // Store Data: "TierId:EventName:Time:IsTruth"
        String data = tierId + ":" + rumorsEventName + ":" + trueTime + ":" + isTruth;
        meta.getPersistentDataContainer().set(RUMOR_KEY, PersistentDataType.STRING, data);
        item.setItemMeta(meta);

        plugin.getInventoryService().giveItem(player, item);
        
        String inflationMsg = config.getString("rumors.messages.price-inflation", "")
                .replace("<price>", Format.currency(finalPrice));
        player.sendMessage(MiniMessage.miniMessage().deserialize(inflationMsg));
    }

    public void revealRumor(Player player, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(RUMOR_KEY, PersistentDataType.STRING)) return;

        String data = meta.getPersistentDataContainer().get(RUMOR_KEY, PersistentDataType.STRING);
        String[] parts = data.split(":"); // Tier:Name:Time:IsTruth
        if (parts.length < 4) return;

        String tierId = parts[0];
        String eventName = parts[1];
        long eventTime = Long.parseLong(parts[2]);
        // boolean isTruth = Boolean.parseBoolean(parts[3]); // We don't reveal truth yet! Only when event happens ideally.

        long msLeft = eventTime - System.currentTimeMillis();
        if (msLeft < 0) msLeft = 0;
        String timeStr = Format.formatDuration(msLeft); // e.g. "12m 30s"

        // Generate Lore based on Detail Level
        String detailLevel = config.getString("rumors.tiers." + tierId + ".detail", "EVENT_NAME");
        String loreKey = "tier-standard-lore";
        String hint = Format.prettifyName(eventName);

        if ("VAGUE".equalsIgnoreCase(detailLevel)) {
            // Map event to a section/category? For now just cryptic name
            loreKey = "tier-cheap-lore";
            hint = "???"; // Would need mapping Event -> Section
        } else if ("PRECISE".equalsIgnoreCase(detailLevel)) {
            loreKey = "tier-insider-lore";
        }

        // Update Item
        meta.displayName(Format.getComponent("rumor-revealed-name"));
        List<Component> lore = new ArrayList<>();
        String rawMsg = config.getString("rumors.messages." + loreKey, "Event: <event>");
        lore.add(MiniMessage.miniMessage().deserialize(rawMsg,
                Placeholder.parsed("event", hint),
                Placeholder.parsed("section", hint), // reuse
                Placeholder.parsed("time", timeStr)
        ));
        
        // Add warning
        lore.add(MiniMessage.miniMessage().deserialize("<dark_gray>Reliability: " + (config.getDouble("rumors.tiers." + tierId + ".accuracy") * 100) + "%</dark_gray>"));
        
        meta.lore(lore);
        meta.getPersistentDataContainer().remove(RUMOR_KEY); // Consume
        item.setItemMeta(meta);
        
        player.sendMessage(MiniMessage.miniMessage().deserialize("<green>You break the seal and read the note...</green>"));
    }
    
    public boolean isRumorItem(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(RUMOR_KEY, PersistentDataType.STRING);
    }

    public Location getCurrentBrokerLocation() {
        return currentBrokerLocation != null ? currentBrokerLocation.location : null;
    }

    /**
     * Broadcast an enhanced message when the Shadow Broker emerges.
     * Shows a fancy header with the region hint.
     */
    private void broadcastBrokerEmergence() {
        String regionName = currentBrokerLocation != null ? currentBrokerLocation.name : "inconnu";

        Bukkit.broadcast(MiniMessage.miniMessage().deserialize(
            "<dark_gray><b>╔══════════════════════════════════════════════════╗</b></dark_gray>"
        ));
        Bukkit.broadcast(MiniMessage.miniMessage().deserialize(
            "<dark_gray><b>║</b></dark_gray> <dark_gray><b>🌙 LE FRATEUR A FAIT SURFACE 🌙</b></dark_gray> <dark_gray><b>║</b></dark_gray>"
        ));
        Bukkit.broadcast(MiniMessage.miniMessage().deserialize(
            "<dark_gray><b>║</b></dark_gray> <gray>On murmure qu'il se cache près de</gray> <gold><b>" + regionName + "</b></gold> <dark_gray><b>║</b></dark_gray>"
        ));
        Bukkit.broadcast(MiniMessage.miniMessage().deserialize(
            "<dark_gray><b>╚══════════════════════════════════════════════════╝</b></dark_gray>"
        ));

        // Also send a subtitle to all online players
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            player.sendActionBar(MiniMessage.miniMessage().deserialize(
                "<gold>Le Frateau est proche de " + regionName + "</gold>"
            ));
        }
    }

    private static class BrokerLocation {
        final Location location;
        final String name;

        BrokerLocation(Location location, String name) {
            this.location = location;
            this.name = name;
        }
    }

    public static class FlashSale {
        public final String itemKey;
        public final double price;
        public int stock;
        public final int discountPercent;

        public FlashSale(String itemKey, double price, int stock, int discountPercent) {
            this.itemKey = itemKey;
            this.price = price;
            this.stock = stock;
            this.discountPercent = discountPercent;
        }
    }
}
