package com.github.lye.pricing.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link ItemConfig}.
 * Verifie la construction, les optionals et le comportement par defaut.
 */
class ItemConfigTest {

    private static final ItemId IRON = new ItemId("minecraft", "iron_ingot");

    // ════════════════════════════════════════════════════════════════
    //  Construction et accessors
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("Tous les champs renseignes")
        void allFields() {
            PricingLocal local = new PricingLocal(0.1, 0.05, 1.0, 100.0, 0.5);
            ItemConfig config = new ItemConfig(IRON, "metals", 50.0, 100, 50, false, local);

            assertEquals(IRON, config.getItemId());
            assertEquals(Optional.of("metals"), config.getSection());
            assertEquals(Optional.of(50.0), config.getPrice());
            assertEquals(Optional.of(100), config.getMaxBuy());
            assertEquals(Optional.of(50), config.getMaxSell());
            assertFalse(config.isFree());
            assertEquals(local, config.getPricingLocal());
        }

        @Test
        @DisplayName("Champs null retournent des Optional.empty()")
        void nullFieldsReturnEmpty() {
            ItemConfig config = new ItemConfig(IRON, null, null, null, null, null, null);

            assertTrue(config.getSection().isEmpty());
            assertTrue(config.getPrice().isEmpty());
            assertTrue(config.getMaxBuy().isEmpty());
            assertTrue(config.getMaxSell().isEmpty());
            assertFalse(config.isFree());
            assertEquals(PricingLocal.EMPTY, config.getPricingLocal());
        }

        @Test
        @DisplayName("pricingLocal null est remplace par EMPTY")
        void nullPricingLocalReplaced() {
            ItemConfig config = new ItemConfig(IRON, null, null, null, null, null, null);
            assertEquals(PricingLocal.EMPTY, config.getPricingLocal());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  isFree
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("isFree: comportement du flag free")
    class IsFree {

        @Test
        @DisplayName("free=true retourne true")
        void freeTrue() {
            ItemConfig config = new ItemConfig(IRON, null, null, null, null, true, null);
            assertTrue(config.isFree());
        }

        @Test
        @DisplayName("free=false retourne false")
        void freeFalse() {
            ItemConfig config = new ItemConfig(IRON, null, null, null, null, false, null);
            assertFalse(config.isFree());
        }

        @Test
        @DisplayName("free=null retourne false (defaut)")
        void freeNull() {
            ItemConfig config = new ItemConfig(IRON, null, null, null, null, null, null);
            assertFalse(config.isFree());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Prix zero et negatifs
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Prix et limites")
    class PriceAndLimits {

        @Test
        @DisplayName("Prix zero est stocke dans l'Optional")
        void zeroPrice() {
            ItemConfig config = new ItemConfig(IRON, null, 0.0, null, null, false, null);
            assertTrue(config.getPrice().isPresent());
            assertEquals(0.0, config.getPrice().get());
        }

        @Test
        @DisplayName("Prix negatif est stocke dans l'Optional")
        void negativePrice() {
            ItemConfig config = new ItemConfig(IRON, null, -10.0, null, null, false, null);
            assertTrue(config.getPrice().isPresent());
            assertEquals(-10.0, config.getPrice().get());
        }

        @Test
        @DisplayName("maxBuy=0 est stocke dans l'Optional")
        void zeroMaxBuy() {
            ItemConfig config = new ItemConfig(IRON, null, null, 0, null, false, null);
            assertTrue(config.getMaxBuy().isPresent());
            assertEquals(0, config.getMaxBuy().get());
        }

        @Test
        @DisplayName("Prix tres grand")
        void veryLargePrice() {
            ItemConfig config = new ItemConfig(IRON, null, Double.MAX_VALUE, null, null, false, null);
            assertEquals(Double.MAX_VALUE, config.getPrice().get());
        }
    }
}
