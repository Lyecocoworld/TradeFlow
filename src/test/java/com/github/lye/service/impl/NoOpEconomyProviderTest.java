package com.github.lye.service.impl;

import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link NoOpEconomyProvider}.
 * Verifie que toutes les operations retournent des valeurs par defaut securitaires.
 */
@ExtendWith(MockitoExtension.class)
class NoOpEconomyProviderTest {

    @Mock
    private OfflinePlayer player;

    private NoOpEconomyProvider provider;

    @BeforeEach
    void setUp() {
        provider = new NoOpEconomyProvider();
    }

    // ════════════════════════════════════════════════════════════════
    //  Comptes joueurs — doivent retourner false
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Comptes joueurs: toutes les operations retournent false")
    class PlayerAccounts {

        @Test
        @DisplayName("hasAccount(player) retourne false")
        void hasAccount_player() {
            assertFalse(provider.hasAccount(player));
        }

        @Test
        @DisplayName("hasAccount(name) retourne false")
        void hasAccount_name() {
            assertFalse(provider.hasAccount("TestAccount"));
        }

        @Test
        @DisplayName("createAccount(player) retourne false")
        void createAccount_player() {
            assertFalse(provider.createAccount(player));
        }

        @Test
        @DisplayName("createAccount(name) retourne false")
        void createAccount_name() {
            assertFalse(provider.createAccount("TestAccount"));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Solde — doit retourner 0.0
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Consultation de solde: toujours 0.0")
    class BalanceQueries {

        @Test
        @DisplayName("getBalance(player) retourne 0.0")
        void getBalance_player() {
            assertEquals(0.0, provider.getBalance(player));
        }

        @Test
        @DisplayName("getBalance(uuid) retourne 0.0")
        void getBalance_uuid() {
            assertEquals(0.0, provider.getBalance(UUID.randomUUID()));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Verification de fonds
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Verification de fonds: toujours false")
    class HasCheck {

        @Test
        @DisplayName("has(player, montant positif) retourne false")
        void has_positive_amount() {
            assertFalse(provider.has(player, 1000.0));
        }

        @Test
        @DisplayName("has(player, 0) retourne false")
        void has_zero_amount() {
            assertFalse(provider.has(player, 0.0));
        }

        @Test
        @DisplayName("has(player, montant negatif) retourne false")
        void has_negative_amount() {
            assertFalse(provider.has(player, -100.0));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Depots — doivent retourner false
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Depots: toujours false")
    class DepositOperations {

        @Test
        @DisplayName("deposit(player, amount) retourne false")
        void deposit_player() {
            assertFalse(provider.deposit(player, 100.0));
        }

        @Test
        @DisplayName("deposit(name, amount) retourne false")
        void deposit_name() {
            assertFalse(provider.deposit("Bank", 500.0));
        }

        @Test
        @DisplayName("deposit(player, 0) retourne false")
        void deposit_zero() {
            assertFalse(provider.deposit(player, 0.0));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Retraits — doivent retourner false
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Retraits: toujours false")
    class WithdrawOperations {

        @Test
        @DisplayName("withdraw(player, amount) retourne false")
        void withdraw_player() {
            assertFalse(provider.withdraw(player, 200.0));
        }

        @Test
        @DisplayName("withdraw(name, amount) retourne false")
        void withdraw_name() {
            assertFalse(provider.withdraw("Bank", 300.0));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Operations bancaires — false / 0.0
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Operations bancaires: false / 0.0")
    class BankOperations {

        @Test
        @DisplayName("hasBankSupport() retourne false")
        void hasBankSupport() {
            assertFalse(provider.hasBankSupport());
        }

        @Test
        @DisplayName("getBankBalance() retourne 0.0")
        void getBankBalance() {
            assertEquals(0.0, provider.getBankBalance("AnyBank"));
        }

        @Test
        @DisplayName("createBank(name, player) retourne false")
        void createBank_player() {
            assertFalse(provider.createBank("Bank", player));
        }

        @Test
        @DisplayName("createBank(name, ownerName) retourne false")
        void createBank_name() {
            assertFalse(provider.createBank("Bank", "Owner"));
        }

        @Test
        @DisplayName("depositToBank() retourne false")
        void depositToBank() {
            assertFalse(provider.depositToBank("Bank", 100.0));
        }

        @Test
        @DisplayName("withdrawFromBank() retourne false")
        void withdrawFromBank() {
            assertFalse(provider.withdrawFromBank("Bank", 50.0));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Idempotence
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Idempotence: appels repetes retournent le meme resultat")
    class Idempotence {

        @Test
        @DisplayName("getBalance est idempotent")
        void getBalance_idempotent() {
            double first = provider.getBalance(player);
            double second = provider.getBalance(player);
            assertEquals(first, second);
            assertEquals(0.0, first);
        }

        @Test
        @DisplayName("deposit est idempotent (toujours false)")
        void deposit_idempotent() {
            assertFalse(provider.deposit(player, 100.0));
            assertFalse(provider.deposit(player, 100.0));
        }

        @Test
        @DisplayName("withdraw est idempotent (toujours false)")
        void withdraw_idempotent() {
            assertFalse(provider.withdraw(player, 50.0));
            assertFalse(provider.withdraw(player, 50.0));
        }
    }
}
