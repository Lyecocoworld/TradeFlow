package com.github.lye.service;

import com.github.lye.data.Shop;
import com.github.lye.data.Transaction;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface ITransactionService {
    void recordTransaction(Player player, Shop shop, int amount, double total, boolean isBuy);
    void recordSellTransaction(UUID uuid, String itemName, Shop itemShop, int amount, double total, double price);

    /**
     * Records a sell transaction with optional recalculation trigger.
     * <p>
     * Use {@code triggerRecalc = false} when batching multiple transactions
     * to avoid N+1 price recalculations; trigger ONE recalculation after the batch.
     *
     * @param uuid         the player's UUID
     * @param itemName     the item identifier
     * @param itemShop     the shop
     * @param amount       the quantity sold
     * @param total        the total monetary value
     * @param price        the unit price
     * @param triggerRecalc whether to trigger a price recalculation after recording
     */
    default void recordSellTransaction(UUID uuid, String itemName, Shop itemShop, int amount, double total, double price, boolean triggerRecalc) {
        recordSellTransaction(uuid, itemName, itemShop, amount, total, price);
    }
}
