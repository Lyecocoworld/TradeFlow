package com.github.lye.gameplay;

import com.github.lye.TradeFlow;
import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.data.Shop;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the invisible economic reputation of players.
 * Tracks if a player trades responsibly with the Central Bank.
 */
public class ReputationManager {

    private final TradeFlow plugin;
    private final Map<UUID, Double> reputationCache = new ConcurrentHashMap<>();

    public ReputationManager(TradeFlow plugin) {
        this.plugin = plugin;
    }

    private IPluginSettings settings() {
        return plugin.getPluginSettings();
    }

    public double getReputation(UUID uuid) {
        return reputationCache.computeIfAbsent(uuid, k -> {
            if (plugin.isMySqlEnabled()) {
                return plugin.getPlayerData().loadReputation(k);
            }
            return settings().getReputationDefault();
        });
    }

    public void addReputation(Player player, double amount) {
        UUID uuid = player.getUniqueId();
        reputationCache.compute(uuid, (k, current) -> {
            if (current == null) {
                current = getReputation(k);
            }
            double next = Math.max(0, Math.min(100, current + amount));
            if (plugin.isMySqlEnabled()) {
                plugin.getServer().getAsyncScheduler().runNow(plugin, task ->
                    plugin.getPlayerData().saveReputation(uuid, next));
            }
            return next;
        });
    }

    /**
     * Logic to determine if a player should gain or lose reputation based on a trade.
     */
    public void processTrade(Player player, Shop shop, int amount, boolean isBuy) {
        int currentStock = plugin.getCentralBankStockManager().getCurrentStock(shop);
        int idealStock = shop.getGlobalStockLimit();
        if (idealStock <= 0) return;

        IPluginSettings s = settings();
        double scarcityThreshold = s.getReputationScarcityThreshold();
        double surplusPriceRatio = s.getReputationSurplusPriceRatio();
        double surplusStockRatio = s.getReputationSurplusStockRatio();

        double delta = 0;

        if (!isBuy) { // Player SELLS to Bank
            if (currentStock < idealStock * scarcityThreshold) {
                // Helping during scarcity! Large boost.
                delta = s.getReputationSellScarcityBonus() * (amount / (double) idealStock * 10.0);
            } else if (currentStock > idealStock * surplusPriceRatio) {
                // Dumping when bank is already full. Penalty.
                delta = s.getReputationSellSurplusPenalty() * (amount / (double) idealStock * 5.0);
            } else {
                // Regular trade. Small boost.
                delta = s.getReputationSellTickBonus();
            }
        } else { // Player BUYS from Bank
            if (currentStock < idealStock * scarcityThreshold) {
                // Hoarding during scarcity! Penalty.
                delta = s.getReputationBuyScarcityPenalty() * (amount / (double) idealStock * 10.0);
            } else if (currentStock > idealStock * surplusStockRatio) {
                // Helping de-stock a surplus! Boost.
                delta = s.getReputationBuySurplusBonus() * (amount / (double) idealStock * 5.0);
            } else {
                delta = s.getReputationBuyTickBonus();
            }
        }

        if (delta != 0) {
            addReputation(player, delta);
        }
    }

    public boolean isInsider(UUID uuid) {
        return getReputation(uuid) >= settings().getReputationInsiderThreshold();
    }

    /**
     * Returns the reputation-based price modifier for a player.
     * <p>
     * Tiers (based on reputation 0-100):
     * <ul>
     *   <li>&lt; 20: penalty-high penalty</li>
     *   <li>20-40: penalty-low penalty</li>
     *   <li>40-60: neutral (0%)</li>
     *   <li>60-80 (Insider): insider-bonus discount</li>
     *   <li>&gt;= 90 (Veteran Insider): veteran-bonus discount</li>
     * </ul>
     * For BUY: negative = discount (lower price), positive = penalty (higher price).
     * For SELL: negative = penalty (lower payout), positive = bonus (higher payout).
     *
     * @param player the player
     * @param isBuy  true for buy, false for sell
     * @return the price modifier (e.g., -0.03 for 3% discount)
     */
    public double getPriceModifier(Player player, boolean isBuy) {
        double reputation = getReputation(player.getUniqueId());
        double tierModifier = getTierModifier(reputation);

        // BUY: positive tier = penalty, negative = discount
        // SELL: invert so high rep = bonus payout, low rep = penalty
        return isBuy ? tierModifier : -tierModifier;
    }

    private double getTierModifier(double reputation) {
        IPluginSettings s = settings();
        if (reputation < 20)  return s.getReputationTierPenaltyHigh();
        if (reputation < 40)  return s.getReputationTierPenaltyLow();
        if (reputation < 60)  return 0.0;
        if (reputation >= 90) return -s.getReputationTierVeteranBonus();
        // 60-90 range (Insider tier, includes isInsider threshold)
        return -s.getReputationTierInsiderBonus();
    }
}
