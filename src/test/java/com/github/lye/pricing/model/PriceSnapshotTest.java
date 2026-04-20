package com.github.lye.pricing.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link PriceSnapshot}.
 * Verifie l'acces aux prix et breakdowns de maniere immuable.
 */
class PriceSnapshotTest {

    private static final ItemId IRON = new ItemId("minecraft", "iron_ingot");
    private static final ItemId GOLD = new ItemId("minecraft", "gold_ingot");
    private static final ItemId DIAMOND = new ItemId("minecraft", "diamond");

    // ════════════════════════════════════════════════════════════════
    //  Construction et acces
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Construction et acces aux prix")
    class PriceAccess {

        @Test
        @DisplayName("Snapshot avec prix vide")
        void emptySnapshot() {
            PriceSnapshot snapshot = new PriceSnapshot(Collections.emptyMap(), Collections.emptyMap());
            assertTrue(snapshot.isEmpty());
            assertTrue(snapshot.getPrice(IRON).isEmpty());
            assertTrue(snapshot.getBreakdown(IRON).isEmpty());
        }

        @Test
        @DisplayName("getPrice retourne le prix correct")
        void getPrice_present() {
            Map<ItemId, Double> prices = Map.of(IRON, 100.0, GOLD, 500.0);
            PriceSnapshot snapshot = new PriceSnapshot(prices, Collections.emptyMap());

            assertEquals(Optional.of(100.0), snapshot.getPrice(IRON));
            assertEquals(Optional.of(500.0), snapshot.getPrice(GOLD));
        }

        @Test
        @DisplayName("getPrice retourne empty si absent")
        void getPrice_absent() {
            Map<ItemId, Double> prices = Map.of(IRON, 100.0);
            PriceSnapshot snapshot = new PriceSnapshot(prices, Collections.emptyMap());

            assertTrue(snapshot.getPrice(DIAMOND).isEmpty());
        }

        @Test
        @DisplayName("isEmpty() retourne false si des prix existent")
        void notEmpty() {
            Map<ItemId, Double> prices = Map.of(IRON, 100.0);
            PriceSnapshot snapshot = new PriceSnapshot(prices, Collections.emptyMap());
            assertFalse(snapshot.isEmpty());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Breakdowns
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Acces aux breakdowns")
    class BreakdownAccess {

        @Test
        @DisplayName("getBreakdown retourne le breakdown correct")
        void getBreakdown_present() {
            Breakdown bd = new Breakdown(IRON, Breakdown.SourceType.SHOP, 100.0,
                    Collections.emptyMap(), 0, 0.1, 0.05, 0.01, 1000.0, "hash");

            Map<ItemId, Breakdown> breakdowns = Map.of(IRON, bd);
            PriceSnapshot snapshot = new PriceSnapshot(Collections.emptyMap(), breakdowns);

            Optional<Breakdown> result = snapshot.getBreakdown(IRON);
            assertTrue(result.isPresent());
            assertEquals(100.0, result.get().getCalculatedPrice());
        }

        @Test
        @DisplayName("getBreakdown retourne empty si absent")
        void getBreakdown_absent() {
            PriceSnapshot snapshot = new PriceSnapshot(Collections.emptyMap(), Collections.emptyMap());
            assertTrue(snapshot.getBreakdown(IRON).isEmpty());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Immutabilite
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Immutabilite")
    class Immutability {

        @Test
        @DisplayName("getPrices() retourne une map non-modifiable")
        void pricesUnmodifiable() {
            Map<ItemId, Double> prices = new HashMap<>();
            prices.put(IRON, 100.0);

            PriceSnapshot snapshot = new PriceSnapshot(prices, Collections.emptyMap());
            assertThrows(UnsupportedOperationException.class, () ->
                    snapshot.getPrices().put(GOLD, 500.0)
            );
        }

        @Test
        @DisplayName("Map null leve NullPointerException")
        void nullPricesMap() {
            assertThrows(NullPointerException.class, () ->
                    new PriceSnapshot(null, Collections.emptyMap())
            );
        }

        @Test
        @DisplayName("Breakdowns null leve NullPointerException")
        void nullBreakdownsMap() {
            assertThrows(NullPointerException.class, () ->
                    new PriceSnapshot(Collections.emptyMap(), null)
            );
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  toString
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("toString affiche les tailles des maps")
    void toString_showsSizes() {
        PriceSnapshot snapshot = new PriceSnapshot(
                Map.of(IRON, 100.0, GOLD, 200.0),
                Collections.emptyMap()
        );
        String str = snapshot.toString();
        assertTrue(str.contains("prices.size=2"));
    }
}
