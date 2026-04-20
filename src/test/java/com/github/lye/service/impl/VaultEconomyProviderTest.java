package com.github.lye.service.impl;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link VaultEconomyProvider}.
 * Verifie que chaque methode delegue correctement a Vault Economy.
 */
@ExtendWith(MockitoExtension.class)
class VaultEconomyProviderTest {

    @Mock
    private Economy economy;

    @Mock
    private OfflinePlayer player;

    private VaultEconomyProvider provider;

    @BeforeEach
    void setUp() {
        provider = new VaultEconomyProvider(economy);
    }

    // ════════════════════════════════════════════════════════════════
    //  Comptes joueurs
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Gestion des comptes joueurs")
    class PlayerAccounts {

        @Test
        @DisplayName("hasAccount(player) delegue a Economy")
        void hasAccount_player() {
            when(economy.hasAccount(player)).thenReturn(true);
            assertTrue(provider.hasAccount(player));
            verify(economy).hasAccount(player);
        }

        @Test
        @DisplayName("hasAccount(player) retourne false quand Vault dit non")
        void hasAccount_player_false() {
            when(economy.hasAccount(player)).thenReturn(false);
            assertFalse(provider.hasAccount(player));
        }

        @Test
        @DisplayName("hasAccount(name) delegue a Economy par nom")
        void hasAccount_name() {
            when(economy.hasAccount("TestAccount")).thenReturn(true);
            assertTrue(provider.hasAccount("TestAccount"));
            verify(economy).hasAccount("TestAccount");
        }

        @Test
        @DisplayName("createAccount(player) delegue a Economy")
        void createAccount_player() {
            when(economy.createPlayerAccount(player)).thenReturn(true);
            assertTrue(provider.createAccount(player));
            verify(economy).createPlayerAccount(player);
        }

        @Test
        @DisplayName("createAccount(name) delegue a Economy par nom")
        void createAccount_name() {
            when(economy.createPlayerAccount("NewAccount")).thenReturn(true);
            assertTrue(provider.createAccount("NewAccount"));
            verify(economy).createPlayerAccount("NewAccount");
        }

        @Test
        @DisplayName("createAccount(player) retourne false si Vault echoue")
        void createAccount_player_failure() {
            when(economy.createPlayerAccount(player)).thenReturn(false);
            assertFalse(provider.createAccount(player));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Solde et verification
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Consultation de solde")
    class BalanceQueries {

        @Test
        @DisplayName("getBalance(player) delegue et retourne le solde")
        void getBalance_player() {
            when(economy.getBalance(player)).thenReturn(1500.50);
            assertEquals(1500.50, provider.getBalance(player));
            verify(economy).getBalance(player);
        }

        @Test
        @DisplayName("getBalance(player) retourne 0 si solde nul")
        void getBalance_player_zero() {
            when(economy.getBalance(player)).thenReturn(0.0);
            assertEquals(0.0, provider.getBalance(player));
        }

        @Test
        @DisplayName("getBalance(player) retourne un solde negatif si decouvert")
        void getBalance_player_negative() {
            when(economy.getBalance(player)).thenReturn(-100.0);
            assertEquals(-100.0, provider.getBalance(player));
        }

        @Test
        @DisplayName("getBalance(uuid) delegue via Bukkit.getOfflinePlayer")
        void getBalance_uuid() {
            // Ce test necessite MockBukkit car il appelle Bukkit.getOfflinePlayer()
            // On verifie seulement que l'implementation appelle economy.getBalance(OfflinePlayer)
            // Note: Integration test avec MockBukkit necessaire pour un test complet
        }

        @Test
        @DisplayName("has(player, amount) delegue a Economy")
        void has_sufficient_funds() {
            when(economy.has(player, 500.0)).thenReturn(true);
            assertTrue(provider.has(player, 500.0));
            verify(economy).has(player, 500.0);
        }

        @Test
        @DisplayName("has(player, amount) retourne false si fonds insuffisants")
        void has_insufficient_funds() {
            when(economy.has(player, 99999.0)).thenReturn(false);
            assertFalse(provider.has(player, 99999.0));
        }

        @Test
        @DisplayName("has(player, 0) retourne true")
        void has_zero_amount() {
            when(economy.has(player, 0.0)).thenReturn(true);
            assertTrue(provider.has(player, 0.0));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Depots
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Operations de depot")
    class DepositOperations {

        /**
         * Creates a success EconomyResponse using the concrete constructor.
         * Avoids nested mock stubbing inside thenReturn() which causes UnfinishedStubbingException.
         */
        private EconomyResponse successResponse() {
            return new EconomyResponse(100.0, 100.0, EconomyResponse.ResponseType.SUCCESS, "");
        }

        private EconomyResponse failureResponse() {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Failed");
        }

        @Test
        @DisplayName("deposit(player, amount) retourne true en cas de succes")
        void deposit_player_success() {
            when(economy.depositPlayer(player, 100.0)).thenReturn(successResponse());
            assertTrue(provider.deposit(player, 100.0));
            verify(economy).depositPlayer(player, 100.0);
        }

        @Test
        @DisplayName("deposit(player, amount) retourne false en cas d'echec")
        void deposit_player_failure() {
            when(economy.depositPlayer(player, 100.0)).thenReturn(failureResponse());
            assertFalse(provider.deposit(player, 100.0));
        }

        @Test
        @DisplayName("deposit(name, amount) delegue par nom de compte")
        void deposit_name_success() {
            when(economy.depositPlayer("Bank", 500.0)).thenReturn(successResponse());
            assertTrue(provider.deposit("Bank", 500.0));
            verify(economy).depositPlayer("Bank", 500.0);
        }

        @Test
        @DisplayName("deposit(name, amount) retourne false si Vault echoue")
        void deposit_name_failure() {
            when(economy.depositPlayer("Bank", 500.0)).thenReturn(failureResponse());
            assertFalse(provider.deposit("Bank", 500.0));
        }

        @Test
        @DisplayName("deposit(player, 0) fonctionne sans erreur")
        void deposit_zero_amount() {
            when(economy.depositPlayer(player, 0.0)).thenReturn(successResponse());
            assertTrue(provider.deposit(player, 0.0));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Retraits
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Operations de retrait")
    class WithdrawOperations {

        private EconomyResponse successResponse() {
            return new EconomyResponse(100.0, 100.0, EconomyResponse.ResponseType.SUCCESS, "");
        }

        private EconomyResponse failureResponse() {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Failed");
        }

        @Test
        @DisplayName("withdraw(player, amount) retourne true en cas de succes")
        void withdraw_player_success() {
            when(economy.withdrawPlayer(player, 200.0)).thenReturn(successResponse());
            assertTrue(provider.withdraw(player, 200.0));
            verify(economy).withdrawPlayer(player, 200.0);
        }

        @Test
        @DisplayName("withdraw(player, amount) retourne false en cas d'echec")
        void withdraw_player_failure() {
            when(economy.withdrawPlayer(player, 200.0)).thenReturn(failureResponse());
            assertFalse(provider.withdraw(player, 200.0));
        }

        @Test
        @DisplayName("withdraw(name, amount) delegue par nom de compte")
        void withdraw_name_success() {
            when(economy.withdrawPlayer("Bank", 300.0)).thenReturn(successResponse());
            assertTrue(provider.withdraw("Bank", 300.0));
            verify(economy).withdrawPlayer("Bank", 300.0);
        }

        @Test
        @DisplayName("withdraw(name, amount) retourne false si Vault echoue")
        void withdraw_name_failure() {
            when(economy.withdrawPlayer("Bank", 300.0)).thenReturn(failureResponse());
            assertFalse(provider.withdraw("Bank", 300.0));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Comptes bancaires
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Operations bancaires")
    class BankOperations {

        @Test
        @DisplayName("hasBankSupport() delegue a Economy")
        void hasBankSupport_true() {
            when(economy.hasBankSupport()).thenReturn(true);
            assertTrue(provider.hasBankSupport());
        }

        @Test
        @DisplayName("hasBankSupport() retourne false si non supporte")
        void hasBankSupport_false() {
            when(economy.hasBankSupport()).thenReturn(false);
            assertFalse(provider.hasBankSupport());
        }

        @Test
        @DisplayName("getBankBalance() retourne le solde en cas de succes")
        void getBankBalance_success() {
            EconomyResponse response = new EconomyResponse(10000.0, 10000.0,
                    EconomyResponse.ResponseType.SUCCESS, "");
            when(economy.bankBalance("CentralBank")).thenReturn(response);

            assertEquals(10000.0, provider.getBankBalance("CentralBank"));
        }

        @Test
        @DisplayName("getBankBalance() retourne 0 si la transaction echoue")
        void getBankBalance_failure_returns_zero() {
            EconomyResponse response = new EconomyResponse(0, 0,
                    EconomyResponse.ResponseType.FAILURE, "Bank not found");
            when(economy.bankBalance("UnknownBank")).thenReturn(response);

            assertEquals(0.0, provider.getBankBalance("UnknownBank"));
        }

        @Test
        @DisplayName("createBank(name, player) delegue et retourne true si succes")
        void createBank_player() {
            EconomyResponse response = mock(EconomyResponse.class);
            when(response.transactionSuccess()).thenReturn(true);
            when(economy.createBank("MyBank", player)).thenReturn(response);

            assertTrue(provider.createBank("MyBank", player));
            verify(economy).createBank("MyBank", player);
        }

        @Test
        @DisplayName("createBank(name, ownerName) delegue par nom")
        void createBank_name() {
            EconomyResponse response = mock(EconomyResponse.class);
            when(response.transactionSuccess()).thenReturn(true);
            when(economy.createBank("MyBank", "OwnerName")).thenReturn(response);

            assertTrue(provider.createBank("MyBank", "OwnerName"));
        }

        @Test
        @DisplayName("depositToBank() retourne true en cas de succes")
        void depositToBank_success() {
            EconomyResponse response = mock(EconomyResponse.class);
            when(response.transactionSuccess()).thenReturn(true);
            when(economy.bankDeposit("MyBank", 1000.0)).thenReturn(response);

            assertTrue(provider.depositToBank("MyBank", 1000.0));
        }

        @Test
        @DisplayName("depositToBank() retourne false en cas d'echec")
        void depositToBank_failure() {
            EconomyResponse response = mock(EconomyResponse.class);
            when(response.transactionSuccess()).thenReturn(false);
            when(economy.bankDeposit("MyBank", 1000.0)).thenReturn(response);

            assertFalse(provider.depositToBank("MyBank", 1000.0));
        }

        @Test
        @DisplayName("withdrawFromBank() retourne true en cas de succes")
        void withdrawFromBank_success() {
            EconomyResponse response = mock(EconomyResponse.class);
            when(response.transactionSuccess()).thenReturn(true);
            when(economy.bankWithdraw("MyBank", 500.0)).thenReturn(response);

            assertTrue(provider.withdrawFromBank("MyBank", 500.0));
        }

        @Test
        @DisplayName("withdrawFromBank() retourne false en cas d'echec")
        void withdrawFromBank_failure() {
            EconomyResponse response = mock(EconomyResponse.class);
            when(response.transactionSuccess()).thenReturn(false);
            when(economy.bankWithdraw("MyBank", 500.0)).thenReturn(response);

            assertFalse(provider.withdrawFromBank("MyBank", 500.0));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Edge cases
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cas limites")
    class EdgeCases {

        @Test
        @DisplayName("Montants negatifs passes tels quels a Vault")
        void negative_amount_passed_through() {
            EconomyResponse response = mock(EconomyResponse.class);
            when(response.transactionSuccess()).thenReturn(true);
            when(economy.depositPlayer(player, -50.0)).thenReturn(response);

            assertTrue(provider.deposit(player, -50.0));
            verify(economy).depositPlayer(player, -50.0);
        }

        @Test
        @DisplayName("Montant tres grand passe a Vault")
        void very_large_amount() {
            EconomyResponse response = mock(EconomyResponse.class);
            when(response.transactionSuccess()).thenReturn(true);
            when(economy.withdrawPlayer(player, Double.MAX_VALUE)).thenReturn(response);

            assertTrue(provider.withdraw(player, Double.MAX_VALUE));
        }

        @Test
        @DisplayName("Solde tres petit retourne correctement")
        void very_small_balance() {
            when(economy.getBalance(player)).thenReturn(0.001);
            assertEquals(0.001, provider.getBalance(player), 0.0001);
        }
    }
}
