package com.github.lye.pricing.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link PricingData}.
 * Verifie la construction, les invariants et l'egalite.
 */
class PricingDataTest {

    private static final ItemId IRON = new ItemId("minecraft", "iron_ingot");
    private static final ItemId GOLD = new ItemId("minecraft", "gold_ingot");

    // ════════════════════════════════════════════════════════════════
    //  Construction
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("Constructeur complet")
        void fullConstructor() {
            long now = System.currentTimeMillis();
            PricingData data = new PricingData(IRON, 100.0, 0.5, now, "abc123");
            assertEquals(IRON, data.getItemId());
            assertEquals(100.0, data.getPrice());
            assertEquals(0.5, data.getVolatility());
            assertEquals(now, data.getLastUpdated());
            assertEquals("abc123", data.getDataHash());
        }

        @Test
        @DisplayName("Constructeur court avec valeurs par defaut")
        void shortConstructor() {
            PricingData data = new PricingData(IRON, 50.0);
            assertEquals(IRON, data.getItemId());
            assertEquals(50.0, data.getPrice());
            assertEquals(0.5, data.getVolatility());
            assertEquals("legacy", data.getDataHash());
            assertTrue(data.getLastUpdated() > 0);
        }

        @Test
        @DisplayName("ItemId null leve NullPointerException")
        void nullItemId() {
            assertThrows(NullPointerException.class, () -> new PricingData(null, 100.0));
        }

        @Test
        @DisplayName("DataHash null leve NullPointerException")
        void nullDataHash() {
            assertThrows(NullPointerException.class, () -> new PricingData(IRON, 100.0, 0.5, 0L, null));
        }

        @Test
        @DisplayName("Prix negatif est autorise (decouvert)")
        void negativePrice() {
            PricingData data = new PricingData(IRON, -50.0);
            assertEquals(-50.0, data.getPrice());
        }

        @Test
        @DisplayName("Prix zero est autorise")
        void zeroPrice() {
            PricingData data = new PricingData(IRON, 0.0);
            assertEquals(0.0, data.getPrice());
        }

        @Test
        @DisplayName("Volatilite 0 est autorise")
        void zeroVolatility() {
            PricingData data = new PricingData(IRON, 100.0, 0.0, 0L, "hash");
            assertEquals(0.0, data.getVolatility());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Egalite
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Egalite et hashCode")
    class Equality {

        @Test
        @DisplayName("Deux PricingData egaux")
        void equalData() {
            PricingData a = new PricingData(IRON, 100.0, 0.5, 1000L, "hash");
            PricingData b = new PricingData(IRON, 100.0, 0.5, 1000L, "hash");
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("PricingData differents par prix")
        void differentPrice() {
            PricingData a = new PricingData(IRON, 100.0, 0.5, 1000L, "hash");
            PricingData b = new PricingData(IRON, 200.0, 0.5, 1000L, "hash");
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("PricingData differents par item")
        void differentItem() {
            PricingData a = new PricingData(IRON, 100.0, 0.5, 1000L, "hash");
            PricingData b = new PricingData(GOLD, 100.0, 0.5, 1000L, "hash");
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("Reflexivite")
        void reflexivity() {
            PricingData data = new PricingData(IRON, 100.0);
            assertEquals(data, data);
        }

        @Test
        @DisplayName("Non egal a null")
        void notEqualToNull() {
            PricingData data = new PricingData(IRON, 100.0);
            assertNotEquals(null, data);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  toString
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("toString contient l'itemId et le prix")
    void toString_containsUsefulInfo() {
        PricingData data = new PricingData(IRON, 100.0);
        String str = data.toString();
        assertTrue(str.contains("iron_ingot"));
        assertTrue(str.contains("100.0"));
    }
}
