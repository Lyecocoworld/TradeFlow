package com.github.lye.service;

import com.github.lye.config.Config;
import com.github.lye.data.CentralBankStockManager;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
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
        if (amount < 0) {
            throw new IllegalArgumentException("Payment amount must be non-negative: amount=" + amount);
        }

        String bankAccountName = config.getCentralBankAccount();
        OfflinePlayer bankAccount = null;

        boolean isSpecialAccount = isSpecialAccount(bankAccountName);

        if (config.isEnableDynamicPricing() && bankAccountName != null && !bankAccountName.isEmpty() && !isSpecialAccount) {
            bankAccount = Bukkit.getOfflinePlayer(bankAccountName);
        }

        if (isBuy) {
            EconomyResponse response = economy.withdrawPlayer(player, amount);
            if (!response.transactionSuccess()) {
                Bukkit.getLogger().warning("[TradeFlow] Payment withdrawal failed for " + player.getName()
                        + ": " + response.errorMessage + " (amount: " + amount + ")");
            }
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
            EconomyResponse response = economy.depositPlayer(player, amount);
            if (!response.transactionSuccess()) {
                Bukkit.getLogger().warning("[TradeFlow] Payment deposit failed for " + player.getName()
                        + ": " + response.errorMessage + " (amount: " + amount + ")");
            }
            centralBankStockManager.removeMoney(amount);
        }
    }

    /**
     * Processes a sell payment where tax is deducted from the payout.
     * <p>
     * The bank pays the full {@code grossTotal} (withdrawn from bank account),
     * but the player only receives {@code netPayout} ({@code grossTotal - tax}).
     * The tax portion is deposited to the treasury separately by
     * {@link com.github.lye.data.TaxManager#collectTaxAsDeduction}.
     * <p>
     * Monetary reserve tracking: the full {@code grossTotal} is removed from the
     * reserve (bank paid it all out). The tax portion is added back when
     * {@code TaxManager.collectTaxAsDeduction} deposits it to the treasury,
     * yielding the same net reserve change as the old deposit-then-withdraw flow.
     *
     * @param player     the player receiving the net payout
     * @param grossTotal the full trade value (before tax deduction)
     * @param netPayout  the amount the player actually receives (grossTotal - tax)
     */
    public void processSellWithNetPayout(Player player, double grossTotal, double netPayout) {
        if (grossTotal < 0) {
            throw new IllegalArgumentException("Payment grossTotal must be non-negative: grossTotal=" + grossTotal);
        }
        if (netPayout < 0) {
            throw new IllegalArgumentException("Payment netPayout must be non-negative: netPayout=" + netPayout);
        }

        String bankAccountName = config.getCentralBankAccount();
        OfflinePlayer bankAccount = null;

        boolean isSpecialAccount = isSpecialAccount(bankAccountName);

        if (config.isEnableDynamicPricing() && bankAccountName != null && !bankAccountName.isEmpty() && !isSpecialAccount) {
            bankAccount = Bukkit.getOfflinePlayer(bankAccountName);
        }

        // Withdraw gross total from bank account (bank's full liability)
        if (bankAccount != null) {
            economy.withdrawPlayer(bankAccount, grossTotal);
        } else if (isSpecialAccount && bankAccountName != null && !bankAccountName.isEmpty()) {
            economy.withdrawPlayer(bankAccountName, grossTotal);
        }

        // Deposit only the net payout to player (tax already deducted)
        EconomyResponse response = economy.depositPlayer(player, netPayout);
        if (!response.transactionSuccess()) {
            Bukkit.getLogger().warning("[TradeFlow] Sell payout deposit failed for " + player.getName()
                    + ": " + response.errorMessage + " (net: " + netPayout + ")");
        }

        // Reserve tracks the gross amount (tax portion returned via TaxManager)
        centralBankStockManager.removeMoney(grossTotal);
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
