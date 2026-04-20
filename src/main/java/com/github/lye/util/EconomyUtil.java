package com.github.lye.util;

import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.data.CentralBankStockManager;
import com.github.lye.service.EconomyProviderFactory;
import com.github.lye.service.IEconomyProvider;
import lombok.experimental.UtilityClass;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The class for managing the economy.
 * <p>
 * Internally delegates to {@link IEconomyProvider} (abstraction over Vault).
 * The static {@link #getEconomy()} method is retained for backward compatibility
 * with callers that still reference the Vault {@code Economy} type directly.
 */
@UtilityClass
public class EconomyUtil {

    private static IEconomyProvider provider;

    // Kept for backward compatibility — callers that still use Vault directly.
    @Nullable
    private static Economy economy;

    /**
     * Returns the underlying Vault {@link Economy} instance, or {@code null}
     * if Vault is not present.
     * <p>
     * <b>Prefer {@link #getProvider()} for new code.</b>
     */
    @Nullable
    public static Economy getEconomy() {
        return economy;
    }

    /**
     * Returns the abstracted economy provider.
     * Falls back to a no-op implementation when Vault is unavailable.
     */
    public static IEconomyProvider getProvider() {
        if (provider == null) {
            return EconomyProviderFactory.createNoOp();
        }
        return provider;
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
     * Initializes the economy via Vault and wraps it in an {@link IEconomyProvider}.
     *
     * @param server The server.
     */
    public static void setupLocalEconomy(@NotNull Server server) {
        RegisteredServiceProvider<Economy> rsp = server.getServicesManager()
                .getRegistration(Economy.class);

        if (rsp == null) {
            provider = EconomyProviderFactory.createNoOp();
            return;
        }

        economy = rsp.getProvider();
        provider = EconomyProviderFactory.create(economy);
    }

    /**
     * Ensures the Central Bank account exists.
     * Tries to create a Player Account first, then a Vault Bank Account if supported.
     */
    public static void ensureCentralBankExists(com.github.lye.TradeFlow plugin) {
        String bankName = plugin.getServices().get(IPluginSettings.class).getCentralBankAccount();
        if (bankName == null || bankName.isEmpty()) return;

        IEconomyProvider prov = getProvider();

        // For special accounts, only try to create by name (not by OfflinePlayer)
        if (isSpecialAccount(bankName)) {
            if (!prov.hasAccount(bankName)) {
                plugin.getLogger().info("[Economy] Creating special account: " + bankName);
                prov.createAccount(bankName);
            }
            return;
        }

        org.bukkit.OfflinePlayer bankPlayer = org.bukkit.Bukkit.getOfflinePlayer(bankName);

        // 1. Check/Create Player Account (by OfflinePlayer)
        if (!prov.hasAccount(bankPlayer)) {
            plugin.getLogger().info("[Economy] Attempting to create Player Account (Object): " + bankName);
            prov.createAccount(bankPlayer);
        }

        // 2. Check/Create Player Account (by Name - often required for NPCs/Virtual)
        if (!prov.hasAccount(bankName)) {
            plugin.getLogger().info("[Economy] Attempting to create Player Account (String): " + bankName);
            prov.createAccount(bankName);
        }

        // 3. Try Bank Account if supported
        if (prov.hasBankSupport()) {
            double balance = prov.getBankBalance(bankName);
            // A zero balance from a non-existent bank is indistinguishable from an
            // existing empty bank, so we always attempt creation — Vault returns
            // false if the bank already exists, which is harmless.
            prov.createBank(bankName, "");
        }
    }

    /**
     * Injects capital into the Central Bank (for initialization).
     */
    public static void injectCapital(double amount, com.github.lye.TradeFlow plugin) {
        ensureCentralBankExists(plugin);
        String bankName = plugin.getServices().get(IPluginSettings.class).getCentralBankAccount();

        plugin.getLogger().info("[Economy] Injecting " + amount + " to " + bankName + "...");

        IEconomyProvider prov = getProvider();
        boolean success = false;

        // For special accounts, only deposit by name
        if (isSpecialAccount(bankName)) {
            success = prov.deposit(bankName, amount);
        } else {
            org.bukkit.OfflinePlayer bankPlayer = org.bukkit.Bukkit.getOfflinePlayer(bankName);
            if (prov.deposit(bankPlayer, amount)) success = true;
            else if (prov.deposit(bankName, amount)) success = true;
            else if (prov.hasBankSupport() && prov.depositToBank(bankName, amount)) success = true;
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
        CentralBankStockManager mgr = plugin.getServices().get(CentralBankStockManager.class);
        if (mgr == null) return 0;
        return mgr.getMonetaryReserve();
    }

    /**
     * Transfers money from the void (or player payment) TO the Central Bank.
     * Used when the server receives money (License fee, Loan repayment, etc).
     */
    public static void transferToCentralBank(double amount, com.github.lye.TradeFlow plugin) {
        CentralBankStockManager mgr = plugin.getServices().get(CentralBankStockManager.class);
        if (mgr != null) {
            mgr.addMoney(amount);
        }

        String bankName = plugin.getServices().get(IPluginSettings.class).getCentralBankAccount();
        if (bankName != null && !bankName.isEmpty()) {
            IEconomyProvider prov = getProvider();
            // For special accounts, only deposit by name
            if (isSpecialAccount(bankName)) {
                prov.deposit(bankName, amount);
            } else {
                org.bukkit.OfflinePlayer bankPlayer = org.bukkit.Bukkit.getOfflinePlayer(bankName);
                if (!prov.deposit(bankPlayer, amount)) {
                    prov.deposit(bankName, amount);
                }
            }
        }
    }

    /**
     * Transfers money FROM the Central Bank TO the void (or player payout).
     * Used when the server pays money (Loan disbursement, Autosell payout).
     */
    public static void transferFromCentralBank(double amount, com.github.lye.TradeFlow plugin) {
        CentralBankStockManager mgr = plugin.getServices().get(CentralBankStockManager.class);
        if (mgr != null) {
            mgr.removeMoney(amount);
        }

        String bankName = plugin.getServices().get(IPluginSettings.class).getCentralBankAccount();
        if (bankName != null && !bankName.isEmpty()) {
            IEconomyProvider prov = getProvider();
            // For special accounts, only withdraw by name
            if (isSpecialAccount(bankName)) {
                prov.withdraw(bankName, amount);
            } else {
                org.bukkit.OfflinePlayer bankPlayer = org.bukkit.Bukkit.getOfflinePlayer(bankName);
                if (!prov.withdraw(bankPlayer, amount)) {
                    prov.withdraw(bankName, amount);
                }
            }
        }
    }

}
