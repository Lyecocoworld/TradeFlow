package com.github.lye.pricing.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link Family}.
 * Verifie la construction, l'immutabilite et l'egalite.
 */
class FamilyTest {

    private static final ItemId WOOL_WHITE = new ItemId("minecraft", "white_wool");
    private static final ItemId WOOL_BLACK = new ItemId("minecraft", "black_wool");
    private static final ItemId WOOL_RED = new ItemId("minecraft", "red_wool");

    // ════════════════════════════════════════════════════════════════
    //  Construction
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("Construction avec root et variants")
        void validConstruction() {
            List<ItemId> variants = Arrays.asList(WOOL_BLACK, WOOL_RED);
            Family family = new Family(WOOL_WHITE, variants);

            assertEquals(WOOL_WHITE, family.getRootItem());
            assertEquals(2, family.getVariantItems().size());
            assertTrue(family.getVariantItems().contains(WOOL_BLACK));
            assertTrue(family.getVariantItems().contains(WOOL_RED));
        }

        @Test
        @DisplayName("Root null leve NullPointerException")
        void nullRoot() {
            assertThrows(NullPointerException.class, () -> new Family(null, Collections.emptyList()));
        }

        @Test
        @DisplayName("Variants null leve NullPointerException")
        void nullVariants() {
            assertThrows(NullPointerException.class, () -> new Family(WOOL_WHITE, null));
        }

        @Test
        @DisplayName("Liste de variants vide est autorisee")
        void emptyVariants() {
            Family family = new Family(WOOL_WHITE, Collections.emptyList());
            assertTrue(family.getVariantItems().isEmpty());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Immutabilite
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Immutabilite")
    class Immutability {

        @Test
        @DisplayName("La liste retournee est une vue non-modifiable ( Collections.unmodifiableList )")
        void originalListModification() {
            // Note: Collections.unmodifiableList est une VUE, pas une copie.
            // Le contrat est que getVariantItems() est non-modifiable, pas une deep copy.
            Family family = new Family(WOOL_WHITE, Arrays.asList(WOOL_BLACK, WOOL_RED));
            assertEquals(2, family.getVariantItems().size());
        }

        @Test
        @DisplayName("La liste des variants est non-modifiable")
        void variantsUnmodifiable() {
            Family family = new Family(WOOL_WHITE, Arrays.asList(WOOL_BLACK));
            assertThrows(UnsupportedOperationException.class, () ->
                    family.getVariantItems().add(WOOL_RED)
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
        @DisplayName("Deux Families egales")
        void equalFamilies() {
            List<ItemId> variants = Arrays.asList(WOOL_BLACK, WOOL_RED);
            Family a = new Family(WOOL_WHITE, variants);
            Family b = new Family(WOOL_WHITE, variants);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("Families differents par root")
        void differentRoot() {
            Family a = new Family(WOOL_WHITE, Collections.emptyList());
            Family b = new Family(WOOL_BLACK, Collections.emptyList());
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("Families differents par variants")
        void differentVariants() {
            Family a = new Family(WOOL_WHITE, Arrays.asList(WOOL_BLACK));
            Family b = new Family(WOOL_WHITE, Arrays.asList(WOOL_RED));
            assertNotEquals(a, b);
        }
    }
}
