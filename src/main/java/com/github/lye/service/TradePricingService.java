package com.github.lye.service;

import com.github.lye.data.CentralBankStockManager;
import com.github.lye.data.Shop;
import com.github.lye.gameplay.ReputationManager;
import com.github.lye.license.LicenseManager;
import org.bukkit.entity.Player;

/**
 * Handles pricing modifiers for trade operations.
 * <p>
 * Extracts dynamic spread calculation, public order bonus, and license modifiers
 * from the monolithic PurchaseUtil into a focused, testable service.
 *
 * @author  lye
 * @since   0.2
 */
public class TradePricingService {

    private final CentralBankStockManager centralBankStockManager;
    private final LicenseManager licenseManager;
    private final ReputationManager reputationManager;

    public TradePricingService(CentralBankStockManager centralBankStockManager,
                               LicenseManager licenseManager,
                               ReputationManager reputationManager) {
        this.centralBankStockManager = centralBankStockManager;
        this.licenseManager = licenseManager;
        this.reputationManager = reputationManager;
    }

    /**
     * Calculates the final unit price with all modifiers applied.
     * <p>
     * Modifier order: base → dynamic spread → public order bonus → license → reputation.
     *
     * @param basePrice the base buy/sell price from Shop
     * @param isBuy     true for buy, false for sell
     * @param shop      the shop item
     * @param player    the player (for license and reputation modifiers)
     * @param itemName  the item name (for dynamic spread lookup)
     * @return the modified unit price, or {@code -1} if trading is suspended (circuit breaker)
     */
    public double calculateFinalPrice(double basePrice, boolean isBuy, Shop shop, Player player, String itemName) {
        double finalUnitPrice = basePrice;
        double dynamicSpread = centralBankStockManager.getDynamicSpread(itemName);

        // Circuit Breaker (Panic Selling protection)
        if (!isBuy && dynamicSpread > 0.45) {
            return -1; // Sentinel: trading suspended due to extreme volatility
        }

        if (isBuy) {
            // Buying from bank: Price increases during high volatility
            finalUnitPrice *= (1.0 + dynamicSpread);
        } else {
            // Selling to bank: Price decreases during high volatility
            finalUnitPrice *= (1.0 - dynamicSpread);

            // Public Order (Commande Publique)
            if (centralBankStockManager.isPublicOrderActive(shop)) {
                double bonus = centralBankStockManager.getPublicOrderBonus();
                finalUnitPrice *= (1.0 + bonus);
            }
        }

        // License modifiers
        if (licenseManager != null) {
            finalUnitPrice = licenseManager.applyModifiers(player, finalUnitPrice, shop.getSection(), isBuy);
        }

        // Reputation modifiers (insiders get better rates)
        if (reputationManager != null) {
            double repModifier = reputationManager.getPriceModifier(player, isBuy);
            finalUnitPrice *= (1.0 + repModifier);
        }

        return finalUnitPrice;
    }

    /**
     * Gets the spread info message if a public order is active for the given shop.
     *
     * @param isBuy    true for buy, false for sell
     * @param shop     the shop item
     * @param itemName the item name for display
     * @return the MiniMessage formatted string, or {@code null} if no public order
     */
    public String getSpreadInfoMessage(boolean isBuy, Shop shop, String itemName) {
        if (!isBuy && centralBankStockManager.isPublicOrderActive(shop)) {
            double bonus = centralBankStockManager.getPublicOrderBonus();
            return "<gold><b>[Commande Publique]</b></gold> <yellow>Le Royaume a un besoin urgent de "
                    + itemName + " ! Bonus de +" + (int) (bonus * 100) + "% appliqué.</yellow>";
        }
        return null;
    }

    /**
     * Applies the reputation price modifier to a given price.
     * <p>
     * Convenience method for inline pricing paths that bypass {@link #calculateFinalPrice}.
     *
     * @param price  the current price
     * @param player the player
     * @param isBuy  true for buy, false for sell
     * @return the price with reputation modifier applied
     */
    public double applyReputationModifier(double price, Player player, boolean isBuy) {
        if (reputationManager == null) return price;
        double repModifier = reputationManager.getPriceModifier(player, isBuy);
        return price * (1.0 + repModifier);
    }
}
