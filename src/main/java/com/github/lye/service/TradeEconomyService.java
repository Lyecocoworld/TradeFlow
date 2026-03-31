package com.github.lye.service;

import com.github.lye.config.Config;
import com.github.lye.data.CentralBankStockManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Handles Vault economy operations and bank transfers.
 * <p>
 * Manages withdraws, deposits, and central bank monetary reserve updates.
 * Extracted from PurchaseUtil's {@code processTransaction()} method.
 *
 * @author  lye
 * @since   0.2
 */
public class TradeEconomyService {

    private final CentralBankStockManager centralBankStockManager;
    private final Config config;
    private final Economy economy;

    public TradeEconomyService(CentralBankStockManager centralBankStockManager,
                               Config config, Economy economy) {
        this.centralBankStockManager = centralBankStockManager;
        this.config = config;
        this.economy = economy;
    }

    /**
     * Processes payment for a transaction (buy or sell).
     * <p>
     * For buys: withdraws from player, deposits to bank account, adds to monetary reserve.
     * For sells: withdraws from bank account, deposits to player, removes from monetary reserve.
     *
     * @param player the player involved in the transaction
     * @param amount the total monetary amount
     * @param isBuy  true for buy (player pays), false for sell (player receives)
     */
    public void processPayment(Player player, double amount, boolean isBuy) {
        String bankAccountName = config.getCentralBankAccount();
        OfflinePlayer bankAccount = null;

        boolean isSpecialAccount = isSpecialAccount(bankAccountName);

        if (config.isEnableDynamicPricing() && bankAccountName != null && !bankAccountName.isEmpty() && !isSpecialAccount) {
            bankAccount = Bukkit.getOfflinePlayer(bankAccountName);
        }

        if (isBuy) {
            economy.withdrawPlayer(player, amount);
            if (bankAccount != null) {
                economy.depositPlayer(bankAccount, amount);
            } else if (isSpecialAccount && bankAccountName != null && !bankAccountName.isEmpty()) {
                economy.depositPlayer(bankAccountName, amount);
            }
            centralBankStockManager.addMoney(amount);
        } else {
            if (bankAccount != null) {
                economy.withdrawPlayer(bankAccount, amount);
            } else if (isSpecialAccount && bankAccountName != null && !bankAccountName.isEmpty()) {
                economy.withdrawPlayer(bankAccountName, amount);
            }
            economy.depositPlayer(player, amount);
            centralBankStockManager.removeMoney(amount);
        }
    }

    /**
     * Checks if an account name is a special (non-player) account.
     * <p>
     * Special accounts like "RoyalTreasury" are not real Minecraft players
     * and should not be queried via {@code Bukkit.getOfflinePlayer()}.
     *
     * @param accountName the account name to check
     * @return true if this is a special/non-player account
     */
    private boolean isSpecialAccount(String accountName) {
        return accountName == null || accountName.isEmpty()
                || "RoyalTreasury".equalsIgnoreCase(accountName)
                || "CentralBank".equalsIgnoreCase(accountName)
                || "ServerTreasury".equalsIgnoreCase(accountName)
                || accountName.startsWith("#");
    }
}
