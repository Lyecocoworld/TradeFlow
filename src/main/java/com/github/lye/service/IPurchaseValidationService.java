package com.github.lye.service;

import com.github.lye.data.Shop;
import org.bukkit.entity.Player;

public interface IPurchaseValidationService {
    /**
     * Validates a purchase/sell using base shop prices (backward-compatible).
     * <p>
     * Does NOT account for dynamic pricing modifiers or tax.
     * Prefer {@link #validatePurchase(Player, Shop, int, boolean, double, double)}
     * for trade flows that use the pricing service.
     */
    boolean validatePurchase(Player player, Shop shop, int amount, boolean isBuy);

    /**
     * Validates a purchase/sell using the actual final total and estimated tax.
     * <p>
     * For buys: checks that the player's balance covers {@code finalTotal + estimatedTax}.
     * For sells: checks bank solvency against {@code finalTotal}.
     *
     * @param player       the player
     * @param shop         the shop item
     * @param amount       the quantity
     * @param isBuy        true for buy, false for sell
     * @param finalTotal   the total price after all modifiers (dynamic spread, license, reputation)
     * @param estimatedTax the estimated tax amount (from {@link com.github.lye.data.TaxManager#estimateTax})
     * @return true if the trade is valid
     */
    boolean validatePurchase(Player player, Shop shop, int amount, boolean isBuy, double finalTotal, double estimatedTax);

    boolean validateSellItemStack(Player player, Shop shop, int amount);
}
