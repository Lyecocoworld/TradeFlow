package com.github.lye.gui;

import com.github.lye.TestUtilities;
import com.github.lye.TradeFlow;
import com.github.lye.gui.state.PlayerShopState;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class GuiNavigatorTest {

    private TradeFlow plugin;
    private GuiNavigator navigator;

    @BeforeEach
    void setUp() {
        plugin = TestUtilities.mockPluginWithServices();
        navigator = new GuiNavigator(plugin);
    }

    // ==================== REFLECTION HELPERS ====================

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<UUID, String> getField(String fieldName) {
        try {
            Field field = GuiNavigator.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (ConcurrentHashMap<UUID, String>) field.get(navigator);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access field: " + fieldName, e);
        }
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<UUID, Integer> getIntField(String fieldName) {
        try {
            Field field = GuiNavigator.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (ConcurrentHashMap<UUID, Integer>) field.get(navigator);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access field: " + fieldName, e);
        }
    }

    private void setCurrentGui(Player player, String guiId) {
        getField("currentGuis").put(player.getUniqueId(), guiId);
    }

    private String getCurrentGui(Player player) {
        return getField("currentGuis").get(player.getUniqueId());
    }

    private void setSectionContext(Player player, String sectionId) {
        getField("sectionContexts").put(player.getUniqueId(), sectionId);
    }

    private void setItemContext(Player player, String itemId) {
        getField("itemContexts").put(player.getUniqueId(), itemId);
    }

    private void setEnchantContext(Player player, String enchantName) {
        getField("enchantContexts").put(player.getUniqueId(), enchantName);
    }

    private void setEnchantLevel(Player player, int level) {
        getIntField("enchantLevels").put(player.getUniqueId(), level);
    }

    // ==================== STATE MANAGEMENT ====================

    @Nested
    @DisplayName("getState() — gestion de l'état joueur")
    class GetState {

        @Test
        @DisplayName("getState crée un état par défaut pour un nouveau joueur")
        void createsStateForNewPlayer() {
            Player player = TestUtilities.mockPlayer();
            PlayerShopState state = navigator.getState(player);

            assertNotNull(state);
            assertEquals(player.getUniqueId(), state.getPlayerId());
        }

        @Test
        @DisplayName("getState retourne la même instance pour le même joueur")
        void returnsSameStateForSamePlayer() {
            Player player = TestUtilities.mockPlayer();
            PlayerShopState first = navigator.getState(player);
            PlayerShopState second = navigator.getState(player);

            assertSame(first, second);
        }

        @Test
        @DisplayName("getState isole les états entre différents joueurs")
        void isolatesStateBetweenPlayers() {
            Player player1 = TestUtilities.mockPlayer();
            Player player2 = TestUtilities.mockPlayer();

            PlayerShopState state1 = navigator.getState(player1);
            PlayerShopState state2 = navigator.getState(player2);

            assertNotSame(state1, state2);
            assertEquals(player1.getUniqueId(), state1.getPlayerId());
            assertEquals(player2.getUniqueId(), state2.getPlayerId());
        }

        @Test
        @DisplayName("getState est thread-safe pour des joueurs concurrents")
        void getStateIsThreadSafe() throws Exception {
            int threadCount = 50;
            UUID uuid = UUID.randomUUID();
            Player player = TestUtilities.mockPlayer(uuid);

            Thread[] threads = new Thread[threadCount];
            PlayerShopState[] results = new PlayerShopState[threadCount];

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                threads[i] = new Thread(() -> results[idx] = navigator.getState(player));
            }

            for (Thread t : threads) t.start();
            for (Thread t : threads) t.join();

            PlayerShopState reference = navigator.getState(player);
            for (PlayerShopState result : results) {
                assertSame(reference, result, "Tous les threads doivent obtenir la même instance");
            }
        }
    }

    // ==================== REMOVE STATE ====================

    @Nested
    @DisplayName("removeState() — suppression de l'état joueur")
    class RemoveState {

        @Test
        @DisplayName("removeState supprime l'état du joueur")
        void removesStateForPlayer() {
            Player player = TestUtilities.mockPlayer();
            PlayerShopState state1 = navigator.getState(player);
            assertNotNull(state1);

            navigator.removeState(player);

            PlayerShopState state2 = navigator.getState(player);
            assertNotSame(state1, state2, "Une nouvelle instance doit être créée après removeState");
        }

        @Test
        @DisplayName("removeState n'affecte pas les autres joueurs")
        void doesNotAffectOtherPlayers() {
            Player player1 = TestUtilities.mockPlayer();
            Player player2 = TestUtilities.mockPlayer();

            PlayerShopState state1 = navigator.getState(player1);
            PlayerShopState state2 = navigator.getState(player2);

            navigator.removeState(player1);

            PlayerShopState state2After = navigator.getState(player2);
            assertSame(state2, state2After, "L'état de player2 ne doit pas changer");
        }

        @Test
        @DisplayName("removeState est idempotent — peut être appelé sans état existant")
        void idempotentWithoutState() {
            Player player = TestUtilities.mockPlayer();
            assertDoesNotThrow(() -> navigator.removeState(player));
        }
    }

    // ==================== CLEANUP ====================

    @Nested
    @DisplayName("cleanup() — nettoyage complet")
    class Cleanup {

        @Test
        @DisplayName("cleanup supprime l'état et tous les contextes")
        void cleanupRemovesAllState() {
            UUID uuid = UUID.randomUUID();
            Player player = TestUtilities.mockPlayer(uuid);

            navigator.getState(player);
            setCurrentGui(player, NavigationHistory.GuiIds.PURCHASE);
            setSectionContext(player, "weapons");
            setItemContext(player, "diamond_sword");
            setEnchantContext(player, "sharpness");
            setEnchantLevel(player, 3);

            navigator.cleanup(player);

            assertNull(getCurrentGui(player));
            assertNull(getField("sectionContexts").get(uuid));
            assertNull(getField("itemContexts").get(uuid));
            assertNull(getField("enchantContexts").get(uuid));
            assertNull(getIntField("enchantLevels").get(uuid));
        }

        @Test
        @DisplayName("cleanup supprime l'état PlayerShopState")
        void cleanupRemovesPlayerShopState() {
            Player player = TestUtilities.mockPlayer();
            PlayerShopState original = navigator.getState(player);
            assertNotNull(original);

            navigator.cleanup(player);

            PlayerShopState newState = navigator.getState(player);
            assertNotSame(original, newState);
        }

        @Test
        @DisplayName("cleanup est idempotent")
        void cleanupIsIdempotent() {
            Player player = TestUtilities.mockPlayer();
            assertDoesNotThrow(() -> navigator.cleanup(player));
            assertDoesNotThrow(() -> navigator.cleanup(player));
        }

        @Test
        @DisplayName("cleanup n'affecte pas les autres joueurs")
        void cleanupDoesNotAffectOtherPlayers() {
            UUID uuid1 = UUID.randomUUID();
            UUID uuid2 = UUID.randomUUID();
            Player player1 = TestUtilities.mockPlayer(uuid1);
            Player player2 = TestUtilities.mockPlayer(uuid2);

            navigator.getState(player1);
            navigator.getState(player2);
            setCurrentGui(player1, NavigationHistory.GuiIds.SECTION);
            setCurrentGui(player2, NavigationHistory.GuiIds.MAIN_MENU);
            setSectionContext(player1, "armor");
            setSectionContext(player2, "weapons");

            navigator.cleanup(player1);

            assertNull(getCurrentGui(player1));
            assertNotNull(getCurrentGui(player2));
            assertEquals(NavigationHistory.GuiIds.MAIN_MENU, getCurrentGui(player2));
            assertEquals("weapons", getField("sectionContexts").get(uuid2));
        }
    }

    // ==================== CAN GO BACK ====================

    @Nested
    @DisplayName("canGoBack() — vérification de navigation retour")
    class CanGoBack {

        @Test
        @DisplayName("canGoBack retourne false si aucun GUI courant")
        void returnsFalseWhenNoCurrentGui() {
            Player player = TestUtilities.mockPlayer();
            assertFalse(navigator.canGoBack(player));
        }

        @Test
        @DisplayName("canGoBack retourne false pour le menu principal (root)")
        void returnsFalseForMainMenu() {
            Player player = TestUtilities.mockPlayer();
            setCurrentGui(player, NavigationHistory.GuiIds.MAIN_MENU);
            assertFalse(navigator.canGoBack(player));
        }

        @Test
        @DisplayName("canGoBack retourne true pour une section (parent = main)")
        void returnsTrueForSection() {
            Player player = TestUtilities.mockPlayer();
            setCurrentGui(player, NavigationHistory.GuiIds.SECTION);
            assertTrue(navigator.canGoBack(player));
        }

        @Test
        @DisplayName("canGoBack retourne true pour un achat (parent = section)")
        void returnsTrueForPurchase() {
            Player player = TestUtilities.mockPlayer();
            setCurrentGui(player, NavigationHistory.GuiIds.PURCHASE);
            assertTrue(navigator.canGoBack(player));
        }

        @Test
        @DisplayName("canGoBack retourne true pour enchant_levels (parent = section)")
        void returnsTrueForEnchantLevels() {
            Player player = TestUtilities.mockPlayer();
            setCurrentGui(player, NavigationHistory.GuiIds.ENCHANT_LEVELS);
            assertTrue(navigator.canGoBack(player));
        }

        @Test
        @DisplayName("canGoBack retourne true pour enchant_purchase (parent = enchant_levels)")
        void returnsTrueForEnchantPurchase() {
            Player player = TestUtilities.mockPlayer();
            setCurrentGui(player, NavigationHistory.GuiIds.ENCHANT_PURCHASE);
            assertTrue(navigator.canGoBack(player));
        }

        @Test
        @DisplayName("canGoBack retourne false pour admin_main (root admin)")
        void returnsFalseForAdminMain() {
            Player player = TestUtilities.mockPlayer();
            setCurrentGui(player, NavigationHistory.GuiIds.ADMIN_MAIN);
            assertFalse(navigator.canGoBack(player));
        }

        @Test
        @DisplayName("canGoBack retourne true pour un GUI admin non-root")
        void returnsTrueForAdminSubGui() {
            Player player = TestUtilities.mockPlayer();
            setCurrentGui(player, NavigationHistory.GuiIds.ADMIN_SYSTEM);
            assertTrue(navigator.canGoBack(player));
        }
    }

    // ==================== CONTEXT STORAGE ====================

    @Nested
    @DisplayName("Contextes de navigation — isolation et persistance")
    class ContextStorage {

        @Test
        @DisplayName("Les contextes de section sont isolés par joueur")
        void sectionContextsIsolatedPerPlayer() {
            UUID uuid1 = UUID.randomUUID();
            UUID uuid2 = UUID.randomUUID();
            Player player1 = TestUtilities.mockPlayer(uuid1);
            Player player2 = TestUtilities.mockPlayer(uuid2);

            setSectionContext(player1, "armor");
            setSectionContext(player2, "weapons");

            assertEquals("armor", getField("sectionContexts").get(uuid1));
            assertEquals("weapons", getField("sectionContexts").get(uuid2));
        }

        @Test
        @DisplayName("Les contextes d'item sont isolés par joueur")
        void itemContextsIsolatedPerPlayer() {
            UUID uuid1 = UUID.randomUUID();
            UUID uuid2 = UUID.randomUUID();
            Player player1 = TestUtilities.mockPlayer(uuid1);
            Player player2 = TestUtilities.mockPlayer(uuid2);

            setItemContext(player1, "diamond_sword");
            setItemContext(player2, "iron_pickaxe");

            assertEquals("diamond_sword", getField("itemContexts").get(uuid1));
            assertEquals("iron_pickaxe", getField("itemContexts").get(uuid2));
        }

        @Test
        @DisplayName("Les niveaux d'enchantement sont isolés par joueur")
        void enchantLevelsIsolatedPerPlayer() {
            UUID uuid1 = UUID.randomUUID();
            UUID uuid2 = UUID.randomUUID();
            Player player1 = TestUtilities.mockPlayer(uuid1);
            Player player2 = TestUtilities.mockPlayer(uuid2);

            setEnchantLevel(player1, 3);
            setEnchantLevel(player2, 5);

            assertEquals(3, getIntField("enchantLevels").get(uuid1));
            assertEquals(5, getIntField("enchantLevels").get(uuid2));
        }

        @Test
        @DisplayName("Le contexte d'enchantement est écrasé par la dernière valeur")
        void enchantContextOverwritten() {
            Player player = TestUtilities.mockPlayer();

            setEnchantContext(player, "sharpness");
            assertEquals("sharpness", getField("enchantContexts").get(player.getUniqueId()));

            setEnchantContext(player, "efficiency");
            assertEquals("efficiency", getField("enchantContexts").get(player.getUniqueId()));
        }

        @Test
        @DisplayName("Le nettoyage supprime les contextes sans affecter les maps globales")
        void cleanupDoesNotCorruptMaps() {
            UUID uuid1 = UUID.randomUUID();
            UUID uuid2 = UUID.randomUUID();
            Player player1 = TestUtilities.mockPlayer(uuid1);
            Player player2 = TestUtilities.mockPlayer(uuid2);

            setSectionContext(player1, "armor");
            setSectionContext(player2, "weapons");
            setItemContext(player1, "helmet");
            setItemContext(player2, "sword");

            navigator.cleanup(player1);

            assertNull(getField("sectionContexts").get(uuid1));
            assertEquals("weapons", getField("sectionContexts").get(uuid2));
            assertNull(getField("itemContexts").get(uuid1));
            assertEquals("sword", getField("itemContexts").get(uuid2));
        }
    }

    // ==================== NAVIGATION HIERARCHY INTEGRATION ====================

    @Nested
    @DisplayName("Intégration hiérarchique — scénarios de navigation")
    class NavigationHierarchyIntegration {

        @Test
        @DisplayName("Parcours complet: contexte conservé à chaque niveau")
        void fullNavigationContextPreserved() {
            UUID uuid = UUID.randomUUID();
            Player player = TestUtilities.mockPlayer(uuid);

            navigator.getState(player);

            setCurrentGui(player, NavigationHistory.GuiIds.SECTION);
            setSectionContext(player, "weapons");

            setCurrentGui(player, NavigationHistory.GuiIds.PURCHASE);
            setItemContext(player, "diamond_sword");

            assertTrue(navigator.canGoBack(player));
            assertEquals("weapons", getField("sectionContexts").get(uuid));
            assertEquals("diamond_sword", getField("itemContexts").get(uuid));
        }

        @Test
        @DisplayName("Parcours enchantement: contexte complet sur 3 niveaux")
        void enchantNavigationContextPreserved() {
            UUID uuid = UUID.randomUUID();
            Player player = TestUtilities.mockPlayer(uuid);

            setCurrentGui(player, NavigationHistory.GuiIds.SECTION);
            setSectionContext(player, "enchantments");

            setCurrentGui(player, NavigationHistory.GuiIds.ENCHANT_LEVELS);
            setEnchantContext(player, "sharpness");

            setCurrentGui(player, NavigationHistory.GuiIds.ENCHANT_PURCHASE);
            setEnchantLevel(player, 5);

            assertTrue(navigator.canGoBack(player));
            assertEquals("enchantments", getField("sectionContexts").get(uuid));
            assertEquals("sharpness", getField("enchantContexts").get(uuid));
            assertEquals(5, getIntField("enchantLevels").get(uuid));
        }

        @Test
        @DisplayName("Double cleanup ne laisse aucune trace")
        void doubleCleanupLeavesNoTrace() {
            UUID uuid = UUID.randomUUID();
            Player player = TestUtilities.mockPlayer(uuid);

            navigator.getState(player);
            setCurrentGui(player, NavigationHistory.GuiIds.PURCHASE);
            setSectionContext(player, "tools");
            setItemContext(player, "pickaxe");

            navigator.cleanup(player);
            navigator.cleanup(player);

            assertNull(getCurrentGui(player));
            assertNull(getField("sectionContexts").get(uuid));
            assertNull(getField("itemContexts").get(uuid));

            Map<UUID, PlayerShopState> stateMap;
            try {
                Field f = GuiNavigator.class.getDeclaredField("stateMap");
                f.setAccessible(true);
                stateMap = (Map<UUID, PlayerShopState>) f.get(navigator);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            assertFalse(stateMap.containsKey(uuid));
        }
    }

    // ==================== ENCHANT CONTEXT VALUE OBJECT ====================

    @Nested
    @DisplayName("EnchantContext — value object")
    class EnchantContextTest {

        @Test
        @DisplayName("EnchantContext stocke nom et niveau")
        void storesNameAndLevel() {
            GuiNavigator.EnchantContext ctx = new GuiNavigator.EnchantContext("sharpness", 5);
            assertEquals("sharpness", ctx.getEnchantName());
            assertEquals(5, ctx.getLevel());
        }

        @Test
        @DisplayName("EnchantContext accepte niveau 0")
        void acceptsLevelZero() {
            GuiNavigator.EnchantContext ctx = new GuiNavigator.EnchantContext("protection", 0);
            assertEquals(0, ctx.getLevel());
        }

        @Test
        @DisplayName("EnchantContext accepte nom vide")
        void acceptsEmptyName() {
            GuiNavigator.EnchantContext ctx = new GuiNavigator.EnchantContext("", 1);
            assertEquals("", ctx.getEnchantName());
        }
    }
}
