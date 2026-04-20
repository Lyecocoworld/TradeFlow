package com.github.lye.pricing.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link ItemId}.
 * Verifie le parsing, l'egalite, le hash et les invariants.
 */
class ItemIdTest {

    // ════════════════════════════════════════════════════════════════
    //  Constructeurs
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Constructeurs")
    class Constructors {

        @Test
        @DisplayName("Constructeur a deux arguments (namespace, key)")
        void twoArgsConstructor() {
            ItemId id = new ItemId("minecraft", "iron_ingot");
            assertEquals("minecraft", id.getNamespace());
            assertEquals("iron_ingot", id.getKey());
            assertEquals("minecraft:iron_ingot", id.getFullId());
        }

        @Test
        @DisplayName("Constructeur a un argument avec namespace complet")
        void singleArgWithNamespace() {
            ItemId id = new ItemId("nexo:custom_sword");
            assertEquals("nexo", id.getNamespace());
            assertEquals("custom_sword", id.getKey());
            assertEquals("nexo:custom_sword", id.getFullId());
        }

        @Test
        @DisplayName("Constructeur a un argument sans namespace -> minecraft par defaut")
        void singleArgWithoutNamespace() {
            ItemId id = new ItemId("diamond");
            assertEquals("minecraft", id.getNamespace());
            assertEquals("diamond", id.getKey());
            assertEquals("minecraft:diamond", id.getFullId());
        }

        @Test
        @DisplayName("Constructeur avec namespace null leve NullPointerException")
        void nullNamespace() {
            assertThrows(NullPointerException.class, () -> new ItemId(null, "key"));
        }

        @Test
        @DisplayName("Constructeur avec key null leve NullPointerException")
        void nullKey() {
            assertThrows(NullPointerException.class, () -> new ItemId("minecraft", null));
        }

        @Test
        @DisplayName("Constructeur a un argument avec null leve NullPointerException")
        void nullFullId() {
            assertThrows(NullPointerException.class, () -> new ItemId(null));
        }

        @Test
        @DisplayName("Namespace avec deux points (premier seulement)")
        void multipleColons() {
            ItemId id = new ItemId("plugin:item:with:colons");
            assertEquals("plugin", id.getNamespace());
            assertEquals("item:with:colons", id.getKey());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Egalite et hash
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Egalite et hashCode")
    class Equality {

        @Test
        @DisplayName("Deux ItemIds egaux (meme namespace + key)")
        void equalItemIds() {
            ItemId a = new ItemId("minecraft", "iron_ingot");
            ItemId b = new ItemId("minecraft", "iron_ingot");
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("ItemIds differents par namespace")
        void differentNamespace() {
            ItemId a = new ItemId("minecraft", "iron_ingot");
            ItemId b = new ItemId("nexo", "iron_ingot");
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("ItemIds differents par key")
        void differentKey() {
            ItemId a = new ItemId("minecraft", "iron_ingot");
            ItemId b = new ItemId("minecraft", "gold_ingot");
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("Reflexivite: un ItemId est egal a lui-meme")
        void reflexivity() {
            ItemId id = new ItemId("minecraft", "diamond");
            assertEquals(id, id);
        }

        @Test
        @DisplayName("Un ItemId n'est pas egal a null")
        void notEqualToNull() {
            ItemId id = new ItemId("minecraft", "diamond");
            assertNotEquals(null, id);
        }

        @Test
        @DisplayName("Un ItemId n'est pas egal a un autre type")
        void notEqualToOtherType() {
            ItemId id = new ItemId("minecraft", "diamond");
            assertNotEquals("minecraft:diamond", id);
        }

        @Test
        @DisplayName("Constructeurs differents, meme resultat semantique")
        void differentConstructorsSameResult() {
            ItemId a = new ItemId("minecraft", "diamond");
            ItemId b = new ItemId("minecraft:diamond");
            assertEquals(a, b);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  toString
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("toString retourne le fullId")
        void toString_returnsFullId() {
            ItemId id = new ItemId("minecraft", "iron_ingot");
            assertEquals("minecraft:iron_ingot", id.toString());
        }

        @Test
        @DisplayName("toString avec namespace custom")
        void toString_customNamespace() {
            ItemId id = new ItemId("nexo", "steel_sword");
            assertEquals("nexo:steel_sword", id.toString());
        }
    }
}
