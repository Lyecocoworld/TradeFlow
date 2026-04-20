package com.github.lye.pricing.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link QItem}.
 * Verifie la construction, les invariants et l'egalite.
 */
class QItemTest {

    private static final ItemId IRON = new ItemId("minecraft", "iron_ingot");
    private static final ItemId GOLD = new ItemId("minecraft", "gold_ingot");

    // ════════════════════════════════════════════════════════════════
    //  Construction
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("Construction valide avec quantite positive")
        void validConstruction() {
            QItem item = new QItem(IRON, 3.0);
            assertEquals(IRON, item.getItem());
            assertEquals(3.0, item.getQty());
        }

        @Test
        @DisplayName("Quantite 1.0 (valeur par defaut commune)")
        void quantityOne() {
            QItem item = new QItem(IRON, 1.0);
            assertEquals(1.0, item.getQty());
        }

        @Test
        @DisplayName("Quantite fractionnaire valide")
        void fractionalQuantity() {
            QItem item = new QItem(IRON, 0.25);
            assertEquals(0.25, item.getQty());
        }

        @Test
        @DisplayName("Quantite 0 leve IllegalArgumentException")
        void zeroQuantity() {
            assertThrows(IllegalArgumentException.class, () -> new QItem(IRON, 0.0));
        }

        @Test
        @DisplayName("Quantite negative leve IllegalArgumentException")
        void negativeQuantity() {
            assertThrows(IllegalArgumentException.class, () -> new QItem(IRON, -1.0));
        }

        @Test
        @DisplayName("Item null leve NullPointerException")
        void nullItem() {
            assertThrows(NullPointerException.class, () -> new QItem(null, 1.0));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Egalite
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Egalite et hashCode")
    class Equality {

        @Test
        @DisplayName("Deux QItems egaux (meme item + quantite)")
        void equalQItems() {
            QItem a = new QItem(IRON, 2.0);
            QItem b = new QItem(IRON, 2.0);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("QItems differents par item")
        void differentItem() {
            QItem a = new QItem(IRON, 2.0);
            QItem b = new QItem(GOLD, 2.0);
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("QItems differents par quantite")
        void differentQuantity() {
            QItem a = new QItem(IRON, 1.0);
            QItem b = new QItem(IRON, 2.0);
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("Reflexivite")
        void reflexivity() {
            QItem item = new QItem(IRON, 1.0);
            assertEquals(item, item);
        }

        @Test
        @DisplayName("Inegalite avec null")
        void notEqualToNull() {
            QItem item = new QItem(IRON, 1.0);
            assertNotEquals(null, item);
        }
    }
}
