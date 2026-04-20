package com.github.lye.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour Loan — modele de pret.
 */
@DisplayName("Loan — Modele de pret")
class LoanTest {

    @Nested
    @DisplayName("Constructeur et getters")
    class ConstructeurGetters {

        @Test
        @DisplayName("Constructeur initialise tous les champs")
        void constructeurInitialise() {
            UUID player = UUID.randomUUID();
            Loan loan = new Loan(1000.0, 800.0, player, false);

            assertEquals(1000.0, loan.getValue(), 0.001);
            assertEquals(800.0, loan.getBase(), 0.001);
            assertEquals(player, loan.getPlayer());
            assertFalse(loan.isPaid());
        }

        @Test
        @DisplayName("Pret marque comme paye")
        void pretPaye() {
            Loan loan = new Loan(500.0, 500.0, UUID.randomUUID(), true);
            assertTrue(loan.isPaid());
        }

        @Test
        @DisplayName("Pret avec valeur zero")
        void pretValeurZero() {
            Loan loan = new Loan(0.0, 0.0, UUID.randomUUID(), false);
            assertEquals(0.0, loan.getValue(), 0.001);
            assertEquals(0.0, loan.getBase(), 0.001);
        }

        @Test
        @DisplayName("Pret avec valeur negative (interet accumule)")
        void pretValeurNegative() {
            Loan loan = new Loan(-100.0, 500.0, UUID.randomUUID(), false);
            assertEquals(-100.0, loan.getValue(), 0.001);
        }
    }

    @Nested
    @DisplayName("Builder")
    class Builder {

        @Test
        @DisplayName("Builder construit un Loan correct")
        void builderConstruit() {
            UUID player = UUID.randomUUID();
            Loan loan = Loan.builder()
                    .value(2000.0)
                    .base(1500.0)
                    .player(player)
                    .paid(false)
                    .build();

            assertEquals(2000.0, loan.getValue(), 0.001);
            assertEquals(1500.0, loan.getBase(), 0.001);
            assertEquals(player, loan.getPlayer());
            assertFalse(loan.isPaid());
        }

        @Test
        @DisplayName("Builder avec valeurs par defaut")
        void builderParDefaut() {
            Loan loan = Loan.builder().build();
            assertEquals(0.0, loan.getValue(), 0.001);
            assertEquals(0.0, loan.getBase(), 0.001);
            assertNull(loan.getPlayer());
            assertFalse(loan.isPaid());
        }
    }
}
