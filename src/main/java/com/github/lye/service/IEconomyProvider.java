package com.github.lye.service;

import org.bukkit.OfflinePlayer;

import java.util.UUID;

/**
 * Abstraction over economy operations.
 * <p>
 * Decouples the plugin from Vault's {@code net.milkbowl.vault.economy.Economy}
 * so that Vault becomes an optional dependency. The production implementation
 * delegates to Vault; a no-op fallback is provided for when Vault is absent.
 *
 * @see EconomyProviderFactory
 * @see com.github.lye.service.impl.VaultEconomyProvider
 * @see com.github.lye.service.impl.NoOpEconomyProvider
 */
public interface IEconomyProvider {

    // ── Player accounts ────────────────────────────────────────────

    boolean hasAccount(OfflinePlayer player);

    boolean hasAccount(String accountName);

    boolean createAccount(OfflinePlayer player);

    boolean createAccount(String accountName);

    double getBalance(OfflinePlayer player);

    double getBalance(UUID playerId);

    boolean has(OfflinePlayer player, double amount);

    boolean deposit(OfflinePlayer player, double amount);

    boolean deposit(String accountName, double amount);

    boolean withdraw(OfflinePlayer player, double amount);

    boolean withdraw(String accountName, double amount);

    // ── Bank accounts ──────────────────────────────────────────────

    boolean hasBankSupport();

    double getBankBalance(String bankName);

    boolean createBank(String bankName, OfflinePlayer owner);

    boolean createBank(String bankName, String ownerName);

    boolean depositToBank(String bankName, double amount);

    boolean withdrawFromBank(String bankName, double amount);
}
