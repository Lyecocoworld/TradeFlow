package com.github.lye.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour Transaction — modele de transaction.
 */
@DisplayName("Transaction — Modele de transaction")
class TransactionTest {

    @Nested
    @DisplayName("Constructeur de compatibilite")
    class ConstructeurCompatibilite {

        @Test
        @DisplayName("Constructeur legacy initialise tous les champs")
        void constructeurLegacy() {
            UUID player = UUID.randomUUID();
            Transaction tx = new Transaction(100.0, 10, player, "DIAMOND", Transaction.TransactionType.BUY);

            assertEquals(100.0, tx.getPrice(), 0.001);
            assertEquals(10, tx.getAmount());
            assertEquals(player, tx.getPlayer());
            assertEquals("DIAMOND", tx.getItem());
            assertEquals(Transaction.TransactionType.BUY, tx.getPosition());
            assertTrue(tx.getTimestamp() > 0);
        }

        @Test
        @DisplayName("Transaction de type SELL")
        void transactionSell() {
            Transaction tx = new Transaction(50.0, 5, UUID.randomUUID(), "GOLD_INGOT", Transaction.TransactionType.SELL);
            assertEquals(Transaction.TransactionType.SELL, tx.getPosition());
        }

        @Test
        @DisplayName("Transaction avec montant zero")
        void transactionMontantZero() {
            Transaction tx = new Transaction(0.0, 0, UUID.randomUUID(), "DIRT", Transaction.TransactionType.BUY);
            assertEquals(0.0, tx.getPrice(), 0.001);
            assertEquals(0, tx.getAmount());
        }

        @Test
        @DisplayName("Transaction avec prix negatif (cas limite)")
        void transactionPrixNegatif() {
            Transaction tx = new Transaction(-10.0, 1, UUID.randomUUID(), "TEST", Transaction.TransactionType.BUY);
            assertEquals(-10.0, tx.getPrice(), 0.001);
        }
    }

    @Nested
    @DisplayName("TransactionType enum")
    class TransactionTypeEnum {

        @Test
        @DisplayName("BUY et SELL existent")
        void typesExist() {
            assertNotNull(Transaction.TransactionType.valueOf("BUY"));
            assertNotNull(Transaction.TransactionType.valueOf("SELL"));
        }

        @Test
        @DisplayName("Exactement 2 types de transaction")
        void deuxTypes() {
            assertEquals(2, Transaction.TransactionType.values().length);
        }
    }

    @Nested
    @DisplayName("Timestamp")
    class Timestamp {

        @Test
        @DisplayName("Timestamp est proche du temps actuel")
        void timestampProche() {
            long before = System.currentTimeMillis();
            Transaction tx = new Transaction(100.0, 1, UUID.randomUUID(), "DIAMOND", Transaction.TransactionType.BUY);
            long after = System.currentTimeMillis();

            assertTrue(tx.getTimestamp() >= before && tx.getTimestamp() <= after);
        }
    }
}
