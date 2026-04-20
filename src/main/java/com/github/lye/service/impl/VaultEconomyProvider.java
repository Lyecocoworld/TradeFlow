package com.github.lye.service.impl;

import com.github.lye.service.IEconomyProvider;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

/**
 * Production implementation of {@link IEconomyProvider} backed by Vault's
 * {@link Economy}.
 * <p>
 * All calls are delegated directly to the Vault {@code Economy} instance
 * obtained from the server's services manager.
 */
public final class VaultEconomyProvider implements IEconomyProvider {

    private final Economy economy;

    public VaultEconomyProvider(Economy economy) {
        this.economy = economy;
    }

    // ── Player accounts ────────────────────────────────────────────

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return economy.hasAccount(player);
    }

    @Override
    public boolean hasAccount(String accountName) {
        return economy.hasAccount(accountName);
    }

    @Override
    public boolean createAccount(OfflinePlayer player) {
        return economy.createPlayerAccount(player);
    }

    @Override
    public boolean createAccount(String accountName) {
        return economy.createPlayerAccount(accountName);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return economy.getBalance(player);
    }

    @Override
    public double getBalance(UUID playerId) {
        return economy.getBalance(org.bukkit.Bukkit.getOfflinePlayer(playerId));
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return economy.has(player, amount);
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response.transactionSuccess();
    }

    @Override
    public boolean deposit(String accountName, double amount) {
        EconomyResponse response = economy.depositPlayer(accountName, amount);
        return response.transactionSuccess();
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    @Override
    public boolean withdraw(String accountName, double amount) {
        EconomyResponse response = economy.withdrawPlayer(accountName, amount);
        return response.transactionSuccess();
    }

    // ── Bank accounts ──────────────────────────────────────────────

    @Override
    public boolean hasBankSupport() {
        return economy.hasBankSupport();
    }

    @Override
    public double getBankBalance(String bankName) {
        EconomyResponse response = economy.bankBalance(bankName);
        return response.transactionSuccess() ? response.balance : 0.0;
    }

    @Override
    public boolean createBank(String bankName, OfflinePlayer owner) {
        EconomyResponse response = economy.createBank(bankName, owner);
        return response.transactionSuccess();
    }

    @Override
    public boolean createBank(String bankName, String ownerName) {
        EconomyResponse response = economy.createBank(bankName, ownerName);
        return response.transactionSuccess();
    }

    @Override
    public boolean depositToBank(String bankName, double amount) {
        EconomyResponse response = economy.bankDeposit(bankName, amount);
        return response.transactionSuccess();
    }

    @Override
    public boolean withdrawFromBank(String bankName, double amount) {
        EconomyResponse response = economy.bankWithdraw(bankName, amount);
        return response.transactionSuccess();
    }
}
