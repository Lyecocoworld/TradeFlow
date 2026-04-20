package com.github.lye.pricing.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link PricingLocal}.
 * Verifie la construction, l'egalite et le singleton EMPTY.
 */
class PricingLocalTest {

    // ════════════════════════════════════════════════════════════════
    //  EMPTY singleton
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Constante EMPTY")
    class EmptyConstant {

        @Test
        @DisplayName("EMPTY a tous les champs null")
        void emptyHasNullFields() {
            assertNull(PricingLocal.EMPTY.getMargin());
            assertNull(PricingLocal.EMPTY.getTax());
            assertNull(PricingLocal.EMPTY.getMinPrice());
            assertNull(PricingLocal.EMPTY.getMaxPrice());
            assertNull(PricingLocal.EMPTY.getVolatility());
        }

        @Test
        @DisplayName("EMPTY est le meme objet (singleton)")
        void emptyIsSameInstance() {
            assertSame(PricingLocal.EMPTY, PricingLocal.EMPTY);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Construction
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("Tous les champs renseignes")
        void allFields() {
            PricingLocal local = new PricingLocal(0.1, 0.05, 1.0, 100.0, 0.5);
            assertEquals(0.1, local.getMargin());
            assertEquals(0.05, local.getTax());
            assertEquals(1.0, local.getMinPrice());
            assertEquals(100.0, local.getMaxPrice());
            assertEquals(0.5, local.getVolatility());
        }

        @Test
        @DisplayName("Tous les champs null")
        void allNull() {
            PricingLocal local = new PricingLocal(null, null, null, null, null);
            assertNull(local.getMargin());
            assertNull(local.getTax());
            assertNull(local.getMinPrice());
            assertNull(local.getMaxPrice());
            assertNull(local.getVolatility());
        }

        @Test
        @DisplayName("Seul le margin est renseigne")
        void onlyMargin() {
            PricingLocal local = new PricingLocal(0.2, null, null, null, null);
            assertEquals(0.2, local.getMargin());
            assertNull(local.getTax());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Egalite
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Egalite et hashCode")
    class Equality {

        @Test
        @DisplayName("Deux PricingLocal egaux")
        void equalInstances() {
            PricingLocal a = new PricingLocal(0.1, 0.05, 1.0, 100.0, 0.5);
            PricingLocal b = new PricingLocal(0.1, 0.05, 1.0, 100.0, 0.5);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("PricingLocal differents par margin")
        void differentMargin() {
            PricingLocal a = new PricingLocal(0.1, null, null, null, null);
            PricingLocal b = new PricingLocal(0.2, null, null, null, null);
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("Deux instances avec tous null sont egales a EMPTY")
        void allNullEqualsEmpty() {
            PricingLocal local = new PricingLocal(null, null, null, null, null);
            assertEquals(PricingLocal.EMPTY, local);
        }

        @Test
        @DisplayName("Reflexivite")
        void reflexivity() {
            PricingLocal local = new PricingLocal(0.1, 0.05, 1.0, 100.0, 0.5);
            assertEquals(local, local);
        }

        @Test
        @DisplayName("Non egal a null")
        void notEqualToNull() {
            PricingLocal local = new PricingLocal(0.1, null, null, null, null);
            assertNotEquals(null, local);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  toString
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("toString contient les champs")
    void toString_containsFields() {
        PricingLocal local = new PricingLocal(0.1, 0.05, 1.0, 100.0, 0.5);
        String str = local.toString();
        assertTrue(str.contains("0.1"));
        assertTrue(str.contains("0.05"));
    }
}
