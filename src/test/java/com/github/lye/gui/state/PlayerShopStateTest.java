package com.github.lye.gui.state;

import com.github.lye.gui.state.PlayerShopState.ShopScreen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerShopStateTest {

    private PlayerShopState state;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        playerId = UUID.randomUUID();
        state = new PlayerShopState(playerId);
    }

    @Nested
    @DisplayName("Constructeur et reset")
    class Constructor {

        @Test
        @DisplayName("Constructeur initialise sur MAIN avec valeurs par défaut")
        void constructorDefaults() {
            assertEquals(playerId, state.getPlayerId());
            assertEquals(ShopScreen.MAIN, state.getScreen());
            assertNull(state.getSectionName());
            assertNull(state.getItemName());
            assertEquals(0, state.getEnchantLevel());
            assertEquals(0, state.getPage());
            assertEquals(1, state.getAmount());
        }

        @Test
        @DisplayName("reset remet toutes les valeurs par défaut")
        void resetRestoresDefaults() {
            state.goToSection("weapons");
            state.goToPurchase("diamond_sword");
            state.setAmount(64);
            state.setPage(3);

            state.reset();

            assertEquals(ShopScreen.MAIN, state.getScreen());
            assertNull(state.getSectionName());
            assertNull(state.getItemName());
            assertEquals(0, state.getEnchantLevel());
            assertEquals(0, state.getPage());
            assertEquals(1, state.getAmount());
        }
    }

    @Nested
    @DisplayName("goToMain()")
    class GoToMain {

        @Test
        @DisplayName("goToMain passe l'écran à MAIN")
        void goToMainSetsScreen() {
            state.goToSection("armor");
            state.goToMain();

            assertEquals(ShopScreen.MAIN, state.getScreen());
            assertNull(state.getSectionName());
            assertNull(state.getItemName());
            assertEquals(0, state.getPage());
            assertEquals(1, state.getAmount());
        }
    }

    @Nested
    @DisplayName("goToSection()")
    class GoToSection {

        @Test
        @DisplayName("goToSection passe l'écran à SECTION et stocke le nom")
        void goToSectionSetsScreenAndName() {
            state.goToSection("weapons");

            assertEquals(ShopScreen.SECTION, state.getScreen());
            assertEquals("weapons", state.getSectionName());
            assertNull(state.getItemName());
            assertEquals(0, state.getEnchantLevel());
            assertEquals(0, state.getPage());
            assertEquals(1, state.getAmount());
        }

        @Test
        @DisplayName("goToSection réinitialise itemName et enchantLevel")
        void goToSectionClearsItemAndEnchant() {
            state.goToSection("armor");
            state.goToPurchase("chestplate");
            state.goToSection("weapons");

            assertNull(state.getItemName());
            assertEquals(0, state.getEnchantLevel());
        }
    }

    @Nested
    @DisplayName("goToPurchase()")
    class GoToPurchase {

        @Test
        @DisplayName("goToPurchase passe l'écran à PURCHASE et stocke l'item")
        void goToPurchaseSetsScreenAndItem() {
            state.goToSection("weapons");
            state.goToPurchase("diamond_sword");

            assertEquals(ShopScreen.PURCHASE, state.getScreen());
            assertEquals("weapons", state.getSectionName());
            assertEquals("diamond_sword", state.getItemName());
            assertEquals(1, state.getAmount());
        }

        @Test
        @DisplayName("goToPurchase préserve sectionName")
        void goToPurchasePreservesSection() {
            state.goToSection("tools");
            state.setPage(5);
            state.goToPurchase("pickaxe");

            assertEquals("tools", state.getSectionName());
            assertEquals(5, state.getPage());
        }
    }

    @Nested
    @DisplayName("goToEnchantLevels()")
    class GoToEnchantLevels {

        @Test
        @DisplayName("goToEnchantLevels passe l'écran à ENCHANT_LEVELS")
        void goToEnchantLevelsSetsScreen() {
            state.goToEnchantLevels("sharpness");

            assertEquals(ShopScreen.ENCHANT_LEVELS, state.getScreen());
            assertEquals("sharpness", state.getItemName());
            assertEquals(0, state.getEnchantLevel());
            assertEquals(1, state.getAmount());
        }
    }

    @Nested
    @DisplayName("goToPurchaseEnchant()")
    class GoToPurchaseEnchant {

        @Test
        @DisplayName("goToPurchaseEnchant passe l'écran et stocke item + level")
        void goToPurchaseEnchantSetsScreenItemLevel() {
            state.goToPurchaseEnchant("efficiency", 3);

            assertEquals(ShopScreen.PURCHASE_ENCHANT, state.getScreen());
            assertEquals("efficiency", state.getItemName());
            assertEquals(3, state.getEnchantLevel());
            assertEquals(1, state.getAmount());
        }
    }

    @Nested
    @DisplayName("setPage()")
    class SetPage {

        @Test
        @DisplayName("setPage avec valeur positive")
        void setPagePositive() {
            state.setPage(5);
            assertEquals(5, state.getPage());
        }

        @Test
        @DisplayName("setPage avec 0 est accepté")
        void setPageZero() {
            state.setPage(0);
            assertEquals(0, state.getPage());
        }

        @Test
        @DisplayName("setPage avec valeur négative est clampé à 0")
        void setPageNegativeClamped() {
            state.setPage(-3);
            assertEquals(0, state.getPage());
        }
    }

    @Nested
    @DisplayName("setAmount()")
    class SetAmount {

        @Test
        @DisplayName("setAmount avec valeur positive")
        void setAmountPositive() {
            state.setAmount(32);
            assertEquals(32, state.getAmount());
        }

        @Test
        @DisplayName("setAmount avec 1 est accepté")
        void setAmountOne() {
            state.setAmount(1);
            assertEquals(1, state.getAmount());
        }

        @Test
        @DisplayName("setAmount avec 0 est clampé à 1")
        void setAmountZeroClamped() {
            state.setAmount(0);
            assertEquals(1, state.getAmount());
        }

        @Test
        @DisplayName("setAmount avec valeur négative est clampé à 1")
        void setAmountNegativeClamped() {
            state.setAmount(-10);
            assertEquals(1, state.getAmount());
        }
    }

    @Nested
    @DisplayName("Transitions complètes")
    class FullTransitions {

        @Test
        @DisplayName("Parcours complet: MAIN → SECTION → PURCHASE → MAIN")
        void fullPlayerJourney() {
            assertEquals(ShopScreen.MAIN, state.getScreen());

            state.goToSection("armor");
            assertEquals(ShopScreen.SECTION, state.getScreen());
            assertEquals("armor", state.getSectionName());

            state.goToPurchase("iron_helmet");
            assertEquals(ShopScreen.PURCHASE, state.getScreen());
            assertEquals("iron_helmet", state.getItemName());
            assertEquals("armor", state.getSectionName());

            state.goToMain();
            assertEquals(ShopScreen.MAIN, state.getScreen());
            assertNull(state.getSectionName());
            assertNull(state.getItemName());
        }

        @Test
        @DisplayName("Parcours enchantement: MAIN → SECTION → ENCHANT_LEVELS → PURCHASE_ENCHANT")
        void enchantJourney() {
            state.goToSection("enchantments");
            state.goToEnchantLevels("unbreaking");
            assertEquals(ShopScreen.ENCHANT_LEVELS, state.getScreen());

            state.goToPurchaseEnchant("unbreaking", 3);
            assertEquals(ShopScreen.PURCHASE_ENCHANT, state.getScreen());
            assertEquals("unbreaking", state.getItemName());
            assertEquals(3, state.getEnchantLevel());
        }

        @Test
        @DisplayName("Plusieurs changements de section nettoyent l'état précédent")
        void multipleSectionChanges() {
            state.goToSection("weapons");
            state.goToPurchase("sword");
            assertEquals("sword", state.getItemName());

            state.goToSection("food");
            assertNull(state.getItemName());
            assertEquals("food", state.getSectionName());
        }
    }

    @Nested
    @DisplayName("ShopScreen enum")
    class ShopScreenEnum {

        @Test
        @DisplayName("Tous les écrans existent")
        void allScreensExist() {
            ShopScreen[] screens = ShopScreen.values();
            assertEquals(5, screens.length);
            assertNotNull(ShopScreen.MAIN);
            assertNotNull(ShopScreen.SECTION);
            assertNotNull(ShopScreen.PURCHASE);
            assertNotNull(ShopScreen.ENCHANT_LEVELS);
            assertNotNull(ShopScreen.PURCHASE_ENCHANT);
        }

        @Test
        @DisplayName("valueOf fonctionne pour chaque écran")
        void valueOfWorks() {
            assertEquals(ShopScreen.MAIN, ShopScreen.valueOf("MAIN"));
            assertEquals(ShopScreen.SECTION, ShopScreen.valueOf("SECTION"));
            assertEquals(ShopScreen.PURCHASE, ShopScreen.valueOf("PURCHASE"));
            assertEquals(ShopScreen.ENCHANT_LEVELS, ShopScreen.valueOf("ENCHANT_LEVELS"));
            assertEquals(ShopScreen.PURCHASE_ENCHANT, ShopScreen.valueOf("PURCHASE_ENCHANT"));
        }
    }
}
