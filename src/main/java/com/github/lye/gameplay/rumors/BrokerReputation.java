package com.github.lye.gameplay.rumors;

import com.github.lye.TradeFlow;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages player reputation with the Shadow Broker.
 * <p>
 * Reputation increases when players purchase from the black market.
 * Higher reputation grants benefits like discounts and increased stock.</p>
 *
 * @author lye
 * @since 0.1
 */
public class BrokerReputation {

    private final TradeFlow plugin;
    private final NamespacedKey REPUTATION_KEY;
    private final Map<UUID, PlayerReputation> cache = new ConcurrentHashMap<>();

    // Reputation thresholds and benefits
    private static final int TIER_NONE = 0;
    private static final int TIER_STRANGER = 1;      // 0-99 points
    private static final int TIER_ACQUAINTANCE = 2;  // 100-499 points
    private static final int TIER_TRUSTED = 3;       // 500-1499 points
    private static final int TIER_VIP = 4;           // 1500-2999 points
    private static final int TIER_INSIDER = 5;       // 3000+ points

    private YamlConfiguration config;

    public BrokerReputation(TradeFlow plugin) {
        this.plugin = plugin;
        this.REPUTATION_KEY = new NamespacedKey(plugin, "broker_reputation");
        loadConfig();
    }

    private void loadConfig() {
        File moduleDir = new File(plugin.getDataFolder(), "modules/rumors");
        if (!moduleDir.exists()) moduleDir.mkdirs();

        File file = new File(moduleDir, "rumors.yml");
        if (!file.exists()) {
            plugin.saveResource("modules/rumors/rumors.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Get a player's reputation data.
     */
    public PlayerReputation getReputation(Player player) {
        UUID uuid = player.getUniqueId();
        if (cache.containsKey(uuid)) {
            return cache.get(uuid);
        }

        PersistentDataContainer data = player.getPersistentDataContainer();
        String stored = data.get(REPUTATION_KEY, PersistentDataType.STRING);

        int points = 0;
        int totalPurchases = 0;

        if (stored != null) {
            String[] parts = stored.split(":");
            if (parts.length >= 2) {
                try {
                    points = Integer.parseInt(parts[0]);
                    totalPurchases = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {}
            }
        }

        PlayerReputation rep = new PlayerReputation(points, totalPurchases);
        cache.put(uuid, rep);
        return rep;
    }

    /**
     * Add reputation points to a player after a purchase.
     */
    public void addReputation(Player player, int points) {
        PlayerReputation rep = getReputation(player);
        rep.points.addAndGet(points);
        rep.totalPurchases.incrementAndGet();
        cache.put(player.getUniqueId(), rep);

        // Save to player data
        String data = rep.points.get() + ":" + rep.totalPurchases.get();
        player.getPersistentDataContainer().set(REPUTATION_KEY, PersistentDataType.STRING, data);

        // Check for tier up
        ReputationTier oldTier = getTier(rep.points.get() - points);
        ReputationTier newTier = getTier(rep.points.get());

        if (newTier.ordinal() > oldTier.ordinal()) {
            // Tier up!
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<dark_gray><b>╔═══════════════════════════════════════╗</b></dark_gray>"
            ));
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<dark_gray><b>║</b></dark_gray> <dark_green><b>⬆ NOUVEAU STATUT: " + newTier.getDisplayName() + "</b></dark_green>"
            ));
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<dark_gray><b>║</b></dark_gray> <gray>" + newTier.getDescription() + "</gray>"
            ));
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<dark_gray><b>╚═══════════════════════════════════════╝</b></dark_gray>"
            ));
        }
    }

    /**
     * Get the reputation tier for a given point value.
     */
    public ReputationTier getTier(int points) {
        if (points >= 3000) return ReputationTier.INSIDER;
        if (points >= 1500) return ReputationTier.VIP;
        if (points >= 500) return ReputationTier.TRUSTED;
        if (points >= 100) return ReputationTier.ACQUAINTANCE;
        return ReputationTier.STRANGER;
    }

    /**
     * Get the discount percentage based on reputation tier.
     */
    public double getDiscount(Player player) {
        PlayerReputation rep = getReputation(player);
        ReputationTier tier = getTier(rep.points.get());
        return config.getDouble("reputation.tiers." + tier.name().toLowerCase() + ".discount", tier.getDiscountPercent() / 100.0);
    }

    /**
     * Get the stock bonus percentage based on reputation tier.
     */
    public int getStockBonus(Player player) {
        PlayerReputation rep = getReputation(player);
        ReputationTier tier = getTier(rep.points.get());
        return config.getInt("reputation.tiers." + tier.name().toLowerCase() + ".stock-bonus", tier.getDiscountPercent() * 2);
    }

    /**
     * Get the proximity hint message based on distance to broker.
     */
    public ProximityLevel getProximityLevel(double distance, double maxDistance) {
        double ratio = distance / maxDistance;

        if (ratio <= 0.15) return ProximityLevel.VERY_CLOSE;
        if (ratio <= 0.30) return ProximityLevel.CLOSE;
        if (ratio <= 0.50) return ProximityLevel.NEARBY;
        if (ratio <= 0.75) return ProximityLevel.WARM;
        return ProximityLevel.FAR;
    }

    /**
     * Clear a player's cached reputation (call on logout).
     */
    public void clearCache(UUID uuid) {
        cache.remove(uuid);
    }

    /**
     * Player reputation data holder.
     */
    public static class PlayerReputation {
        public final AtomicInteger points;
        public final AtomicInteger totalPurchases;

        public PlayerReputation(int points, int totalPurchases) {
            this.points = new AtomicInteger(points);
            this.totalPurchases = new AtomicInteger(totalPurchases);
        }

        public int getPoints() { return points.get(); }
        public int getTotalPurchases() { return totalPurchases.get(); }
    }

    /**
     * Reputation tiers with associated benefits.
     */
    public enum ReputationTier {
        STRANGER("<gray>Étranger</gray>", "Le Frateur ne te connaît pas encore.", 0),
        ACQUAINTANCE("<white>Connaissance</white>", "Le Frateur te reconnaît.", 5),
        TRUSTED("<green>De Confiance</green>", "Le Frateur te fait confiance.", 10),
        VIP("<gold>VIP</gold>", "Tu es un client privilégié du Frateur.", 15),
        INSIDER("<dark_purple><b>Initié</b></dark_purple>", "Le Frâteau te considère comme un des siens.", 25);

        private final String displayName;
        private final String description;
        private final int discountPercent;

        ReputationTier(String displayName, String description, int discountPercent) {
            this.displayName = displayName;
            this.description = description;
            this.discountPercent = discountPercent;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public int getDiscountPercent() { return discountPercent; }
    }

    /**
     * Proximity levels for the hot/cold system.
     */
    public enum ProximityLevel {
        VERY_CLOSE(4, "<green><b>✅ Tu es tout près ! Cherche autour de toi...</b></green>"),
        CLOSE(3, "<green><b>🟡 Tu es très proche ! Continue !</b></green>"),
        NEARBY(2, "<yellow><b>🟠 Tu te rapproches... Le Frateau est dans le coin.</b></yellow>"),
        WARM(1, "<gold><b>🧡 Tu es sur la bonne voie...</b></gold>"),
        FAR(0, "<dark_gray><b>❌ Tu es très loin du Frateau...</b></dark_gray>");

        private final int level;
        private final String message;

        ProximityLevel(int level, String message) {
            this.level = level;
            this.message = message;
        }

        public int getLevel() { return level; }
        public String getMessage() { return message; }
    }
}
