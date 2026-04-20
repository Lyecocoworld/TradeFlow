package com.github.lye.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GuiHierarchyTest {

    @Nested
    @DisplayName("getParent()")
    class GetParent {

        @Test
        @DisplayName("MAIN_MENU n'a pas de parent (root)")
        void mainMenuNoParent() {
            assertNull(GuiHierarchy.getParent(NavigationHistory.GuiIds.MAIN_MENU));
        }

        @Test
        @DisplayName("SECTION a pour parent MAIN_MENU")
        void sectionParentIsMainMenu() {
            assertEquals(NavigationHistory.GuiIds.MAIN_MENU,
                    GuiHierarchy.getParent(NavigationHistory.GuiIds.SECTION));
        }

        @Test
        @DisplayName("PURCHASE a pour parent SECTION")
        void purchaseParentIsSection() {
            assertEquals(NavigationHistory.GuiIds.SECTION,
                    GuiHierarchy.getParent(NavigationHistory.GuiIds.PURCHASE));
        }

        @Test
        @DisplayName("ENCHANT_LEVELS a pour parent SECTION")
        void enchantLevelsParentIsSection() {
            assertEquals(NavigationHistory.GuiIds.SECTION,
                    GuiHierarchy.getParent(NavigationHistory.GuiIds.ENCHANT_LEVELS));
        }

        @Test
        @DisplayName("ENCHANT_PURCHASE a pour parent ENCHANT_LEVELS")
        void enchantPurchaseParentIsEnchantLevels() {
            assertEquals(NavigationHistory.GuiIds.ENCHANT_LEVELS,
                    GuiHierarchy.getParent(NavigationHistory.GuiIds.ENCHANT_PURCHASE));
        }

        @Test
        @DisplayName("UTILITY a pour parent MAIN_MENU")
        void utilityParentIsMainMenu() {
            assertEquals(NavigationHistory.GuiIds.MAIN_MENU,
                    GuiHierarchy.getParent(NavigationHistory.GuiIds.UTILITY));
        }

        @Test
        @DisplayName("LICENSE a pour parent UTILITY")
        void licenseParentIsUtility() {
            assertEquals(NavigationHistory.GuiIds.UTILITY,
                    GuiHierarchy.getParent(NavigationHistory.GuiIds.LICENSE));
        }

        @Test
        @DisplayName("STATS_SELECTION a pour parent UTILITY")
        void statsSelectionParentIsUtility() {
            assertEquals(NavigationHistory.GuiIds.UTILITY,
                    GuiHierarchy.getParent(NavigationHistory.GuiIds.STATS_SELECTION));
        }

        @Test
        @DisplayName("PLAYER_STATS a pour parent STATS_SELECTION")
        void playerStatsParentIsStatsSelection() {
            assertEquals(NavigationHistory.GuiIds.STATS_SELECTION,
                    GuiHierarchy.getParent(NavigationHistory.GuiIds.PLAYER_STATS));
        }

        @Test
        @DisplayName("SERVER_STATS a pour parent STATS_SELECTION")
        void serverStatsParentIsStatsSelection() {
            assertEquals(NavigationHistory.GuiIds.STATS_SELECTION,
                    GuiHierarchy.getParent(NavigationHistory.GuiIds.SERVER_STATS));
        }

        @Test
        @DisplayName("ADMIN_MAIN n'a pas de parent (root)")
        void adminMainNoParent() {
            assertNull(GuiHierarchy.getParent(NavigationHistory.GuiIds.ADMIN_MAIN));
        }

        @Test
        @DisplayName("ADMIN_SYSTEM a pour parent ADMIN_MAIN")
        void adminSystemParentIsAdminMain() {
            assertEquals(NavigationHistory.GuiIds.ADMIN_MAIN,
                    GuiHierarchy.getParent(NavigationHistory.GuiIds.ADMIN_SYSTEM));
        }

        @Test
        @DisplayName("null retourne null")
        void nullReturnsNull() {
            assertNull(GuiHierarchy.getParent(null));
        }

        @Test
        @DisplayName("ID inconnu retourne null")
        void unknownReturnsNull() {
            assertNull(GuiHierarchy.getParent("unknown_gui"));
        }
    }

    @Nested
    @DisplayName("isRoot()")
    class IsRoot {

        @Test
        @DisplayName("MAIN_MENU est root")
        void mainMenuIsRoot() {
            assertTrue(GuiHierarchy.isRoot(NavigationHistory.GuiIds.MAIN_MENU));
        }

        @Test
        @DisplayName("ADMIN_MAIN est root")
        void adminMainIsRoot() {
            assertTrue(GuiHierarchy.isRoot(NavigationHistory.GuiIds.ADMIN_MAIN));
        }

        @Test
        @DisplayName("SECTION n'est pas root")
        void sectionIsNotRoot() {
            assertFalse(GuiHierarchy.isRoot(NavigationHistory.GuiIds.SECTION));
        }

        @Test
        @DisplayName("PURCHASE n'est pas root")
        void purchaseIsNotRoot() {
            assertFalse(GuiHierarchy.isRoot(NavigationHistory.GuiIds.PURCHASE));
        }

        @Test
        @DisplayName("null n'est pas root")
        void nullIsNotRoot() {
            assertFalse(GuiHierarchy.isRoot(null));
        }
    }

    @Nested
    @DisplayName("isDefined()")
    class IsDefined {

        @Test
        @DisplayName("MAIN_MENU est défini")
        void mainMenuDefined() {
            assertTrue(GuiHierarchy.isDefined(NavigationHistory.GuiIds.MAIN_MENU));
        }

        @Test
        @DisplayName("SECTION est défini")
        void sectionDefined() {
            assertTrue(GuiHierarchy.isDefined(NavigationHistory.GuiIds.SECTION));
        }

        @Test
        @DisplayName("ID inconnu n'est pas défini")
        void unknownNotDefined() {
            assertFalse(GuiHierarchy.isDefined("nonexistent"));
        }

        @Test
        @DisplayName("null n'est pas défini")
        void nullNotDefined() {
            assertFalse(GuiHierarchy.isDefined(null));
        }
    }

    @Nested
    @DisplayName("getRoot()")
    class GetRoot {

        @Test
        @DisplayName("getRoot d'un root retourne lui-même")
        void rootOfRoot() {
            assertEquals(NavigationHistory.GuiIds.MAIN_MENU,
                    GuiHierarchy.getRoot(NavigationHistory.GuiIds.MAIN_MENU));
        }

        @Test
        @DisplayName("getRoot remonte ENCHANT_PURCHASE → ENCHANT_LEVELS → SECTION → MAIN_MENU")
        void rootOfDeepHierarchy() {
            assertEquals(NavigationHistory.GuiIds.MAIN_MENU,
                    GuiHierarchy.getRoot(NavigationHistory.GuiIds.ENCHANT_PURCHASE));
        }

        @Test
        @DisplayName("getRoot de PLAYER_STATS → MAIN_MENU")
        void rootOfPlayerStats() {
            assertEquals(NavigationHistory.GuiIds.MAIN_MENU,
                    GuiHierarchy.getRoot(NavigationHistory.GuiIds.PLAYER_STATS));
        }

        @Test
        @DisplayName("getRoot de ADMIN_ECONOMY → ADMIN_MAIN")
        void rootOfAdminEconomy() {
            assertEquals(NavigationHistory.GuiIds.ADMIN_MAIN,
                    GuiHierarchy.getRoot(NavigationHistory.GuiIds.ADMIN_ECONOMY));
        }

        @Test
        @DisplayName("getRoot null retourne null")
        void rootOfNull() {
            assertNull(GuiHierarchy.getRoot(null));
        }

        @Test
        @DisplayName("getRoot d'un ID inconnu retourne null")
        void rootOfUnknown() {
            assertNull(GuiHierarchy.getRoot("nonexistent"));
        }
    }

    @Nested
    @DisplayName("getDepth()")
    class GetDepth {

        @Test
        @DisplayName("Root a depth 0")
        void rootDepthZero() {
            assertEquals(0, GuiHierarchy.getDepth(NavigationHistory.GuiIds.MAIN_MENU));
            assertEquals(0, GuiHierarchy.getDepth(NavigationHistory.GuiIds.ADMIN_MAIN));
        }

        @Test
        @DisplayName("SECTION a depth 1")
        void sectionDepthOne() {
            assertEquals(1, GuiHierarchy.getDepth(NavigationHistory.GuiIds.SECTION));
        }

        @Test
        @DisplayName("PURCHASE a depth 2")
        void purchaseDepthTwo() {
            assertEquals(2, GuiHierarchy.getDepth(NavigationHistory.GuiIds.PURCHASE));
        }

        @Test
        @DisplayName("ENCHANT_PURCHASE a depth 3")
        void enchantPurchaseDepthThree() {
            assertEquals(3, GuiHierarchy.getDepth(NavigationHistory.GuiIds.ENCHANT_PURCHASE));
        }

        @Test
        @DisplayName("PLAYER_STATS a depth 3 (MAIN→UTILITY→STATS_SELECTION→PLAYER_STATS)")
        void playerStatsDepthThree() {
            assertEquals(3, GuiHierarchy.getDepth(NavigationHistory.GuiIds.PLAYER_STATS));
        }

        @Test
        @DisplayName("null retourne -1")
        void nullReturnsMinusOne() {
            assertEquals(-1, GuiHierarchy.getDepth(null));
        }

        @Test
        @DisplayName("ID inconnu retourne -1")
        void unknownReturnsMinusOne() {
            assertEquals(-1, GuiHierarchy.getDepth("nonexistent"));
        }
    }

    @Nested
    @DisplayName("Cohérence de la hiérarchie")
    class HierarchyConsistency {

        @Test
        @DisplayName("Tous les chemins remontent vers un root")
        void allPathsLeadToRoot() {
            String[] allIds = {
                    NavigationHistory.GuiIds.SECTION,
                    NavigationHistory.GuiIds.PURCHASE,
                    NavigationHistory.GuiIds.ENCHANT_LEVELS,
                    NavigationHistory.GuiIds.ENCHANT_PURCHASE,
                    NavigationHistory.GuiIds.UTILITY,
                    NavigationHistory.GuiIds.LICENSE,
                    NavigationHistory.GuiIds.STATS_SELECTION,
                    NavigationHistory.GuiIds.PLAYER_STATS,
                    NavigationHistory.GuiIds.SERVER_STATS,
                    NavigationHistory.GuiIds.ORGANIZATION_STATS,
                    NavigationHistory.GuiIds.HELP,
                    NavigationHistory.GuiIds.DOCS,
                    NavigationHistory.GuiIds.RUMOR,
                    NavigationHistory.GuiIds.BLACK_MARKET,
                    NavigationHistory.GuiIds.ADMIN_SYSTEM,
                    NavigationHistory.GuiIds.ADMIN_ECONOMY,
                    NavigationHistory.GuiIds.ADMIN_SHOPS,
                    NavigationHistory.GuiIds.ADMIN_TRANSACTIONS,
                    NavigationHistory.GuiIds.ADMIN_NOTIFICATIONS,
                    NavigationHistory.GuiIds.ADMIN_PLAYERS,
                    NavigationHistory.GuiIds.ADMIN_STATS
            };

            for (String id : allIds) {
                String root = GuiHierarchy.getRoot(id);
                assertNotNull(root, "Root ne devrait pas être null pour " + id);
                assertTrue(GuiHierarchy.isRoot(root), "Le root de " + id + " devrait être un root");
            }
        }

        @Test
        @DisplayName("Aucun cycle dans la hiérarchie")
        void noCycles() {
            String[] allIds = {
                    NavigationHistory.GuiIds.MAIN_MENU,
                    NavigationHistory.GuiIds.SECTION,
                    NavigationHistory.GuiIds.PURCHASE,
                    NavigationHistory.GuiIds.ENCHANT_LEVELS,
                    NavigationHistory.GuiIds.ENCHANT_PURCHASE,
                    NavigationHistory.GuiIds.UTILITY,
                    NavigationHistory.GuiIds.LICENSE,
                    NavigationHistory.GuiIds.STATS_SELECTION,
                    NavigationHistory.GuiIds.PLAYER_STATS,
                    NavigationHistory.GuiIds.SERVER_STATS,
                    NavigationHistory.GuiIds.ORGANIZATION_STATS,
                    NavigationHistory.GuiIds.HELP,
                    NavigationHistory.GuiIds.DOCS,
                    NavigationHistory.GuiIds.RUMOR,
                    NavigationHistory.GuiIds.BLACK_MARKET,
                    NavigationHistory.GuiIds.ADMIN_MAIN,
                    NavigationHistory.GuiIds.ADMIN_SYSTEM,
                    NavigationHistory.GuiIds.ADMIN_ECONOMY,
                    NavigationHistory.GuiIds.ADMIN_SHOPS,
                    NavigationHistory.GuiIds.ADMIN_TRANSACTIONS,
                    NavigationHistory.GuiIds.ADMIN_NOTIFICATIONS,
                    NavigationHistory.GuiIds.ADMIN_PLAYERS,
                    NavigationHistory.GuiIds.ADMIN_STATS
            };

            for (String id : allIds) {
                String current = id;
                int maxSteps = 10;
                while (current != null && maxSteps-- > 0) {
                    current = GuiHierarchy.getParent(current);
                }
                assertTrue(maxSteps > 0, "Cycle détecté pour " + id);
            }
        }

        @Test
        @DisplayName("Deux racines distinctes: MAIN_MENU et ADMIN_MAIN")
        void twoDistinctRoots() {
            assertTrue(GuiHierarchy.isRoot(NavigationHistory.GuiIds.MAIN_MENU));
            assertTrue(GuiHierarchy.isRoot(NavigationHistory.GuiIds.ADMIN_MAIN));
            assertNotEquals(NavigationHistory.GuiIds.MAIN_MENU, NavigationHistory.GuiIds.ADMIN_MAIN);
        }

        @Test
        @DisplayName("Les GUIs admin ne remontent pas vers MAIN_MENU")
        void adminGuiRootIsAdminMain() {
            assertEquals(NavigationHistory.GuiIds.ADMIN_MAIN,
                    GuiHierarchy.getRoot(NavigationHistory.GuiIds.ADMIN_ECONOMY));
            assertEquals(NavigationHistory.GuiIds.ADMIN_MAIN,
                    GuiHierarchy.getRoot(NavigationHistory.GuiIds.ADMIN_SHOPS));
            assertEquals(NavigationHistory.GuiIds.ADMIN_MAIN,
                    GuiHierarchy.getRoot(NavigationHistory.GuiIds.ADMIN_PLAYERS));
        }
    }
}
