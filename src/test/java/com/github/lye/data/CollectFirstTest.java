package com.github.lye.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour CollectFirst — parametre de collection.
 */
@DisplayName("CollectFirst — Parametre de collection")
class CollectFirstTest {

    @Nested
    @DisplayName("Constructeur par chaine")
    class ConstructeurChaine {

        @Test
        @DisplayName("'player' donne CollectFirstSetting.PLAYER")
        void playerSetting() {
            CollectFirst cf = new CollectFirst("player");
            assertEquals(CollectFirst.CollectFirstSetting.PLAYER, cf.getSetting());
        }

        @Test
        @DisplayName("'PLAYER' (majuscule) donne aussi PLAYER")
        void playerMajuscule() {
            CollectFirst cf = new CollectFirst("PLAYER");
            assertEquals(CollectFirst.CollectFirstSetting.PLAYER, cf.getSetting());
        }

        @Test
        @DisplayName("'server' donne CollectFirstSetting.SERVER")
        void serverSetting() {
            CollectFirst cf = new CollectFirst("server");
            assertEquals(CollectFirst.CollectFirstSetting.SERVER, cf.getSetting());
        }

        @Test
        @DisplayName("'SERVER' donne aussi SERVER")
        void serverMajuscule() {
            CollectFirst cf = new CollectFirst("SERVER");
            assertEquals(CollectFirst.CollectFirstSetting.SERVER, cf.getSetting());
        }

        @Test
        @DisplayName("'NONE' donne CollectFirstSetting.NONE")
        void noneSetting() {
            CollectFirst cf = new CollectFirst("NONE");
            assertEquals(CollectFirst.CollectFirstSetting.NONE, cf.getSetting());
        }

        @Test
        @DisplayName("Chaine inconnue donne NONE par defaut")
        void chaineInconnueDonneNone() {
            CollectFirst cf = new CollectFirst("unknown");
            assertEquals(CollectFirst.CollectFirstSetting.NONE, cf.getSetting());
        }

        @Test
        @DisplayName("Chaine vide donne NONE")
        void chaineVideDonneNone() {
            CollectFirst cf = new CollectFirst("");
            assertEquals(CollectFirst.CollectFirstSetting.NONE, cf.getSetting());
        }
    }

    @Nested
    @DisplayName("Constructeur avec enum et boolean")
    class ConstructeurEnumBoolean {

        @Test
        @DisplayName("Constructeur enum initialise correctement")
        void constructeurEnum() {
            CollectFirst cf = new CollectFirst(CollectFirst.CollectFirstSetting.PLAYER, true);
            assertEquals(CollectFirst.CollectFirstSetting.PLAYER, cf.getSetting());
            assertTrue(cf.isFoundInServer());
        }

        @Test
        @DisplayName("foundInServer est false par defaut via constructeur chaine")
        void foundInServerFalseParDefaut() {
            CollectFirst cf = new CollectFirst("player");
            assertFalse(cf.isFoundInServer());
        }
    }

    @Nested
    @DisplayName("Setter foundInServer")
    class FoundInServerSetter {

        @Test
        @DisplayName("setFoundInServer(true) met a jour")
        void setFoundTrue() {
            CollectFirst cf = new CollectFirst("player");
            cf.setFoundInServer(true);
            assertTrue(cf.isFoundInServer());
        }

        @Test
        @DisplayName("setFoundInServer(false) met a jour")
        void setFoundFalse() {
            CollectFirst cf = new CollectFirst(CollectFirst.CollectFirstSetting.SERVER, true);
            cf.setFoundInServer(false);
            assertFalse(cf.isFoundInServer());
        }
    }

    @Nested
    @DisplayName("Enum CollectFirstSetting")
    class EnumTests {

        @Test
        @DisplayName("Trois valeurs dans l'enum")
        void troisValeurs() {
            assertEquals(3, CollectFirst.CollectFirstSetting.values().length);
            assertNotNull(CollectFirst.CollectFirstSetting.valueOf("PLAYER"));
            assertNotNull(CollectFirst.CollectFirstSetting.valueOf("SERVER"));
            assertNotNull(CollectFirst.CollectFirstSetting.valueOf("NONE"));
        }
    }
}
