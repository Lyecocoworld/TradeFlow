package com.github.lye.service.impl;

import com.github.lye.service.IEconomyProvider;
import org.bukkit.OfflinePlayer;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Null-object implementation of {@link IEconomyProvider}.
 * <p>
 * Used when no economy plugin (Vault) is available. Every mutating operation
 * logs a warning and returns {@code false}; every query returns zero / empty
 * defaults. This prevents {@link NoClassDefFoundError} when Vault is absent
 * and keeps the rest of the plugin functional in degraded mode.
 */
public final class NoOpEconomyProvider implements IEconomyProvider {

    private static final Logger LOGGER = Logger.getLogger(NoOpEconomyProvider.class.getName());

    private static void warn(String operation) {
        LOGGER.warning("[Economy] NoOpEconomyProvider: '" + operation + "' called without an economy provider.");
    }

    // ── Player accounts ────────────────────────────────────────────

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return false;
    }

    @Override
    public boolean hasAccount(String accountName) {
        return false;
    }

    @Override
    public boolean createAccount(OfflinePlayer player) {
        warn("createAccount(player)");
        return false;
    }

    @Override
    public boolean createAccount(String accountName) {
        warn("createAccount(name)");
        return false;
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return 0.0;
    }

    @Override
    public double getBalance(UUID playerId) {
        return 0.0;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return false;
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        warn("deposit(player, " + amount + ")");
        return false;
    }

    @Override
    public boolean deposit(String accountName, double amount) {
        warn("deposit(name, " + amount + ")");
        return false;
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        warn("withdraw(player, " + amount + ")");
        return false;
    }

    @Override
    public boolean withdraw(String accountName, double amount) {
        warn("withdraw(name, " + amount + ")");
        return false;
    }

    // ── Bank accounts ──────────────────────────────────────────────

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public double getBankBalance(String bankName) {
        return 0.0;
    }

    @Override
    public boolean createBank(String bankName, OfflinePlayer owner) {
        warn("createBank(name, player)");
        return false;
    }

    @Override
    public boolean createBank(String bankName, String ownerName) {
        warn("createBank(name, string)");
        return false;
    }

    @Override
    public boolean depositToBank(String bankName, double amount) {
        warn("depositToBank(" + bankName + ", " + amount + ")");
        return false;
    }

    @Override
    public boolean withdrawFromBank(String bankName, double amount) {
        warn("withdrawFromBank(" + bankName + ", " + amount + ")");
        return false;
    }
}
