package com.github.lye.pricing.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link PriceBreakdown}.
 * Verifie la construction (record), withFinalPrice et les invariants.
 */
class PriceBreakdownTest {

    private static final ItemId IRON = new ItemId("minecraft", "iron_ingot");

    private PriceBreakdown createSample() {
        return new PriceBreakdown(
                "minecraft:iron_ingot",
                100.0,   // finalPrice
                80.0,    // basePrice
                60.0,    // rawMaterialsCost
                5.0,     // energyCost
                0.0,     // byproductCredit
                0.10,    // margin
                0.05,    // tax
                false,   // isAnchor
                null     // recipeUsed
        );
    }

    // ════════════════════════════════════════════════════════════════
    //  Construction
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("Tous les champs sont accessibles")
        void allFieldsAccessible() {
            PriceBreakdown bd = createSample();
            assertEquals("minecraft:iron_ingot", bd.itemId());
            assertEquals(100.0, bd.finalPrice());
            assertEquals(80.0, bd.basePrice());
            assertEquals(60.0, bd.rawMaterialsCost());
            assertEquals(5.0, bd.energyCost());
            assertEquals(0.0, bd.byproductCredit());
            assertEquals(0.10, bd.margin());
            assertEquals(0.05, bd.tax());
            assertFalse(bd.isAnchor());
            assertNull(bd.recipeUsed());
        }

        @Test
        @DisplayName("itemId null leve NullPointerException")
        void nullItemId() {
            assertThrows(NullPointerException.class, () -> new PriceBreakdown(
                    null, 100.0, 80.0, 60.0, 5.0, 0.0, 0.1, 0.05, false, null
            ));
        }

        @Test
        @DisplayName("itemId vide est accepte (pas de validation specifique)")
        void emptyItemId() {
            PriceBreakdown bd = new PriceBreakdown(
                    "", 100.0, 80.0, 60.0, 5.0, 0.0, 0.1, 0.05, false, null
            );
            assertEquals("", bd.itemId());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  withFinalPrice
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("withFinalPrice: copie avec nouveau prix")
    class WithFinalPrice {

        @Test
        @DisplayName("withFinalPrice cree une nouvelle instance avec le nouveau prix")
        void createsNewInstance() {
            PriceBreakdown original = createSample();
            PriceBreakdown modified = original.withFinalPrice(200.0);

            assertEquals(200.0, modified.finalPrice());
            assertEquals(100.0, original.finalPrice(), "L'original ne doit pas changer");
            assertNotSame(original, modified);
        }

        @Test
        @DisplayName("withFinalPrice preserve tous les autres champs")
        void preservesOtherFields() {
            PriceBreakdown original = createSample();
            PriceBreakdown modified = original.withFinalPrice(200.0);

            assertEquals(original.itemId(), modified.itemId());
            assertEquals(original.basePrice(), modified.basePrice());
            assertEquals(original.rawMaterialsCost(), modified.rawMaterialsCost());
            assertEquals(original.energyCost(), modified.energyCost());
            assertEquals(original.byproductCredit(), modified.byproductCredit());
            assertEquals(original.margin(), modified.margin());
            assertEquals(original.tax(), modified.tax());
            assertEquals(original.isAnchor(), modified.isAnchor());
            assertEquals(original.recipeUsed(), modified.recipeUsed());
        }

        @Test
        @DisplayName("withFinalPrice avec prix 0")
        void zeroPrice() {
            PriceBreakdown modified = createSample().withFinalPrice(0.0);
            assertEquals(0.0, modified.finalPrice());
        }

        @Test
        @DisplayName("withFinalPrice avec prix negatif")
        void negativePrice() {
            PriceBreakdown modified = createSample().withFinalPrice(-50.0);
            assertEquals(-50.0, modified.finalPrice());
        }

        @Test
        @DisplayName("Chained withFinalPrice")
        void chainedCalls() {
            PriceBreakdown bd = createSample()
                    .withFinalPrice(200.0)
                    .withFinalPrice(300.0);
            assertEquals(300.0, bd.finalPrice());
        }
    }
}
