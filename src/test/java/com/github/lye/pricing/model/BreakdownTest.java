package com.github.lye.pricing.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link Breakdown}.
 * Verifie la construction, l'immutabilite et l'egalite.
 */
class BreakdownTest {

    private static final ItemId IRON = new ItemId("minecraft", "iron_ingot");
    private static final ItemId COAL = new ItemId("minecraft", "coal");

    // ════════════════════════════════════════════════════════════════
    //  Construction
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("Construction complete avec tous les champs")
        void fullConstruction() {
            Map<ItemId, Double> inputs = Map.of(COAL, 10.0);

            Breakdown bd = new Breakdown(
                    IRON, Breakdown.SourceType.AUTO, 100.0,
                    inputs, 5.0, 0.10, 0.05, 0.01, 1000.0, "stablehash"
            );

            assertEquals(IRON, bd.getItemId());
            assertEquals(Breakdown.SourceType.AUTO, bd.getSourceType());
            assertEquals(100.0, bd.getCalculatedPrice());
            assertEquals(5.0, bd.getEnergyCost());
            assertEquals(0.10, bd.getMargin());
            assertEquals(0.05, bd.getTax());
            assertEquals(0.01, bd.getMinPrice());
            assertEquals(1000.0, bd.getMaxPrice());
            assertEquals("stablehash", bd.getStableHash());
            assertEquals(1, bd.getInputs().size());
        }

        @Test
        @DisplayName("ItemId null leve NullPointerException")
        void nullItemId() {
            assertThrows(NullPointerException.class, () -> new Breakdown(
                    null, Breakdown.SourceType.AUTO, 100.0,
                    Collections.emptyMap(), 0, 0, 0, 0, 0, "hash"
            ));
        }

        @Test
        @DisplayName("SourceType null leve NullPointerException")
        void nullSourceType() {
            assertThrows(NullPointerException.class, () -> new Breakdown(
                    IRON, null, 100.0,
                    Collections.emptyMap(), 0, 0, 0, 0, 0, "hash"
            ));
        }

        @Test
        @DisplayName("Inputs null leve NullPointerException")
        void nullInputs() {
            assertThrows(NullPointerException.class, () -> new Breakdown(
                    IRON, Breakdown.SourceType.AUTO, 100.0,
                    null, 0, 0, 0, 0, 0, "hash"
            ));
        }

        @Test
        @DisplayName("StableHash null leve NullPointerException")
        void nullHash() {
            assertThrows(NullPointerException.class, () -> new Breakdown(
                    IRON, Breakdown.SourceType.AUTO, 100.0,
                    Collections.emptyMap(), 0, 0, 0, 0, 0, null
            ));
        }

        @Test
        @DisplayName("SourceType enum a les valeurs SHOP et AUTO")
        void sourceTypeValues() {
            Breakdown.SourceType[] types = Breakdown.SourceType.values();
            assertEquals(2, types.length);
            assertEquals(Breakdown.SourceType.SHOP, Breakdown.SourceType.valueOf("SHOP"));
            assertEquals(Breakdown.SourceType.AUTO, Breakdown.SourceType.valueOf("AUTO"));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Immutabilite
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Immutabilite")
    class Immutability {

        @Test
        @DisplayName("La map des inputs est non-modifiable")
        void inputsUnmodifiable() {
            Map<ItemId, Double> inputs = new java.util.HashMap<>();
            inputs.put(COAL, 10.0);

            Breakdown bd = new Breakdown(IRON, Breakdown.SourceType.AUTO, 100.0,
                    inputs, 0, 0, 0, 0, 0, "hash");

            assertThrows(UnsupportedOperationException.class, () ->
                    bd.getInputs().put(IRON, 5.0)
            );
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Egalite
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Egalite")
    class Equality {

        @Test
        @DisplayName("Deux Breakdowns egaux")
        void equalBreakdowns() {
            Breakdown a = new Breakdown(IRON, Breakdown.SourceType.AUTO, 100.0,
                    Collections.emptyMap(), 0, 0, 0, 0, 0, "hash");
            Breakdown b = new Breakdown(IRON, Breakdown.SourceType.AUTO, 100.0,
                    Collections.emptyMap(), 0, 0, 0, 0, 0, "hash");
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("Breakdowns differents par prix")
        void differentPrice() {
            Breakdown a = new Breakdown(IRON, Breakdown.SourceType.AUTO, 100.0,
                    Collections.emptyMap(), 0, 0, 0, 0, 0, "hash");
            Breakdown b = new Breakdown(IRON, Breakdown.SourceType.AUTO, 200.0,
                    Collections.emptyMap(), 0, 0, 0, 0, 0, "hash");
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("Breakdowns differents par sourceType")
        void differentSourceType() {
            Breakdown a = new Breakdown(IRON, Breakdown.SourceType.AUTO, 100.0,
                    Collections.emptyMap(), 0, 0, 0, 0, 0, "hash");
            Breakdown b = new Breakdown(IRON, Breakdown.SourceType.SHOP, 100.0,
                    Collections.emptyMap(), 0, 0, 0, 0, 0, "hash");
            assertNotEquals(a, b);
        }
    }
}
