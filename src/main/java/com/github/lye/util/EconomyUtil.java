package com.github.lye.util;

import lombok.Getter;
import lombok.experimental.UtilityClass;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;

/**
 * The class for managing the economy.
 */
@UtilityClass
public class EconomyUtil {

    private static Economy economy;

    // Explicit static getter to avoid Lombok reliance during compilation
    public static Economy getEconomy() {
        return economy;
    }

    /**
     * Checks if an account name is a special (non-player) account.
     * Special accounts should not be queried via Bukkit.getOfflinePlayer()
     * as they are not real Minecraft players and may cause API errors.
     */
    private static boolean isSpecialAccount(String accountName) {
        return accountName == null || accountName.isEmpty()
                || "RoyalTreasury".equalsIgnoreCase(accountName)
                || "CentralBank".equalsIgnoreCase(accountName)
                || "ServerTreasury".equalsIgnoreCase(accountName)
                || accountName.startsWith("#"); // Prefix convention for special accounts
    }

    /**
     * Initializes the economy.
     *
     * @param server The server.
     */
    public static void setupLocalEconomy(@NotNull Server server) {
        RegisteredServiceProvider<Economy> rsp = server.getServicesManager()
                .getRegistration(Economy.class);

        if (rsp == null) {
            return;
        }

        economy = rsp.getProvider();
    }

    /**
     * Ensures the Central Bank account exists.
     * Tries to create a Player Account first, then a Vault Bank Account if supported.
     */
    public static void ensureCentralBankExists(com.github.lye.TradeFlow plugin) {
        String bankName = plugin.getPluginSettings().getCentralBankAccount();
        if (bankName == null || bankName.isEmpty()) return;

        // For special accounts, only try to create by name (not by OfflinePlayer)
        if (isSpecialAccount(bankName)) {
            if (!economy.hasAccount(bankName)) {
                plugin.getLogger().info("[Economy] Creating special account: " + bankName);
                economy.createPlayerAccount(bankName);
            }
            return;
        }

        org.bukkit.OfflinePlayer bankPlayer = org.bukkit.Bukkit.getOfflinePlayer(bankName);

        // 1. Check/Create Player Account (by OfflinePlayer)
        if (!economy.hasAccount(bankPlayer)) {
            plugin.getLogger().info("[Economy] Attempting to create Player Account (Object): " + bankName);
            economy.createPlayerAccount(bankPlayer);
        }

        // 2. Check/Create Player Account (by Name - often required for NPCs/Virtual)
        if (!economy.hasAccount(bankName)) {
            plugin.getLogger().info("[Economy] Attempting to create Player Account (String): " + bankName);
            economy.createPlayerAccount(bankName);
        }

        // 3. Try Bank Account if supported
        if (economy.hasBankSupport()) {
            net.milkbowl.vault.economy.EconomyResponse r = economy.bankBalance(bankName);
            if (!r.transactionSuccess()) {
                plugin.getLogger().info("[Economy] Attempting to create Vault Bank: " + bankName);
                economy.createBank(bankName, "");
            }
        }
    }

    /**
     * Injects capital into the Central Bank (for initialization).
     */
    public static void injectCapital(double amount, com.github.lye.TradeFlow plugin) {
        ensureCentralBankExists(plugin);
        String bankName = plugin.getPluginSettings().getCentralBankAccount();

        plugin.getLogger().info("[Economy] Injecting " + amount + " to " + bankName + "...");

        boolean success = false;

        // For special accounts, only deposit by name
        if (isSpecialAccount(bankName)) {
            success = economy.depositPlayer(bankName, amount).transactionSuccess();
        } else {
            org.bukkit.OfflinePlayer bankPlayer = org.bukkit.Bukkit.getOfflinePlayer(bankName);
            if (economy.depositPlayer(bankPlayer, amount).transactionSuccess()) success = true;
            else if (economy.depositPlayer(bankName, amount).transactionSuccess()) success = true;
            else if (economy.hasBankSupport() && economy.bankDeposit(bankName, amount).transactionSuccess()) success = true;
        }

        if (success) {
            plugin.getLogger().info("[Economy] Injection successful. New balance: " + getCentralBankBalance(plugin));
        } else {
            plugin.getLogger().severe("[Economy] Injection FAILED! The economy plugin rejected the deposit for '" + bankName + "'.");
        }
    }

    /**
     * Gets the total balance of the Central Bank (Internal Reserve).
     */
    public static double getCentralBankBalance(com.github.lye.TradeFlow plugin) {
        if (plugin.getCentralBankStockManager() == null) return 0;
        return plugin.getCentralBankStockManager().getMonetaryReserve();
    }

    /**
     * Transfers money from the void (or player payment) TO the Central Bank.
     * Used when the server receives money (License fee, Loan repayment, etc).
     */
    public static void transferToCentralBank(double amount, com.github.lye.TradeFlow plugin) {
        if (plugin.getCentralBankStockManager() != null) {
            plugin.getCentralBankStockManager().addMoney(amount);
        }

        // Optional: Sync with external account for visual tracking if configured
        String bankName = plugin.getPluginSettings().getCentralBankAccount();
        if (bankName != null && !bankName.isEmpty()) {
            // For special accounts, only deposit by name
            if (isSpecialAccount(bankName)) {
                economy.depositPlayer(bankName, amount);
            } else {
                org.bukkit.OfflinePlayer bankPlayer = org.bukkit.Bukkit.getOfflinePlayer(bankName);
                if (!economy.depositPlayer(bankPlayer, amount).transactionSuccess()) {
                    economy.depositPlayer(bankName, amount);
                }
            }
        }
    }

    /**
     * Transfers money FROM the Central Bank TO the void (or player payout).
     * Used when the server pays money (Loan disbursement, Autosell payout).
     */
    public static void transferFromCentralBank(double amount, com.github.lye.TradeFlow plugin) {
        if (plugin.getCentralBankStockManager() != null) {
            plugin.getCentralBankStockManager().removeMoney(amount);
        }

        // Optional: Sync with external account
        String bankName = plugin.getPluginSettings().getCentralBankAccount();
        if (bankName != null && !bankName.isEmpty()) {
            // For special accounts, only withdraw by name
            if (isSpecialAccount(bankName)) {
                economy.withdrawPlayer(bankName, amount);
            } else {
                org.bukkit.OfflinePlayer bankPlayer = org.bukkit.Bukkit.getOfflinePlayer(bankName);
                if (!economy.withdrawPlayer(bankPlayer, amount).transactionSuccess()) {
                    economy.withdrawPlayer(bankName, amount);
                }
            }
        }
    }

}
