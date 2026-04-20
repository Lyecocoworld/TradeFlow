package com.github.lye.gui;

import com.github.lye.gui.NavigationHistory.Entry;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Deque;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NavigationHistoryTest {

    private Player player;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        playerId = UUID.randomUUID();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        NavigationHistory.clearAll();
    }

    @AfterEach
    void tearDown() {
        NavigationHistory.clearAll();
    }

    @Nested
    @DisplayName("push()")
    class Push {

        @Test
        @DisplayName("push ajoute une entrée dans l'historique")
        void pushAddsEntry() {
            NavigationHistory.push(player, NavigationHistory.GuiIds.MAIN_MENU);

            assertEquals(1, NavigationHistory.size(player));
            Entry current = NavigationHistory.peek(player);
            assertNotNull(current);
            assertEquals(NavigationHistory.GuiIds.MAIN_MENU, current.getGuiId());
        }

        @Test
        @DisplayName("push avec contexte stocke le contexte")
        void pushWithContextStoresContext() {
            Object ctx = new Object();
            NavigationHistory.push(player, new Entry(NavigationHistory.GuiIds.SECTION, ctx));

            Entry entry = NavigationHistory.peek(player);
            assertNotNull(entry);
            assertSame(ctx, entry.getContext());
        }

        @Test
        @DisplayName("push empile plusieurs entrées dans l'ordre LIFO")
        void pushStacksMultipleEntries() {
            NavigationHistory.push(player, NavigationHistory.GuiIds.MAIN_MENU);
            NavigationHistory.push(player, NavigationHistory.GuiIds.SECTION);
            NavigationHistory.push(player, NavigationHistory.GuiIds.PURCHASE);

            assertEquals(3, NavigationHistory.size(player));
            assertEquals(NavigationHistory.GuiIds.PURCHASE, NavigationHistory.peek(player).getGuiId());
        }

        @Test
        @DisplayName("push pour différents joueurs est isolé")
        void pushIsolatedPerPlayer() {
            Player player2 = mock(Player.class);
            when(player2.getUniqueId()).thenReturn(UUID.randomUUID());

            NavigationHistory.push(player, NavigationHistory.GuiIds.MAIN_MENU);
            NavigationHistory.push(player2, NavigationHistory.GuiIds.HELP);

            assertEquals(1, NavigationHistory.size(player));
            assertEquals(1, NavigationHistory.size(player2));
            assertEquals(NavigationHistory.GuiIds.MAIN_MENU, NavigationHistory.peek(player).getGuiId());
            assertEquals(NavigationHistory.GuiIds.HELP, NavigationHistory.peek(player2).getGuiId());
        }
    }

    @Nested
    @DisplayName("goBack()")
    class GoBack {

        @Test
        @DisplayName("goBack retourne null si historique vide")
        void goBackEmptyReturnsNull() {
            assertNull(NavigationHistory.goBack(player));
        }

        @Test
        @DisplayName("goBack retourne null si un seul élément")
        void goBackSingleEntryReturnsNull() {
            NavigationHistory.push(player, NavigationHistory.GuiIds.MAIN_MENU);

            assertNull(NavigationHistory.goBack(player));
        }

        @Test
        @DisplayName("goBack retire l'entrée courante et retourne la précédente")
        void goBackPopsAndReturnsPrevious() {
            NavigationHistory.push(player, NavigationHistory.GuiIds.MAIN_MENU);
            NavigationHistory.push(player, NavigationHistory.GuiIds.SECTION);
            NavigationHistory.push(player, NavigationHistory.GuiIds.PURCHASE);

            Entry previous = NavigationHistory.goBack(player);

            assertNotNull(previous);
            assertEquals(NavigationHistory.GuiIds.SECTION, previous.getGuiId());
            assertEquals(2, NavigationHistory.size(player));
        }

        @Test
        @DisplayName("goBack successive remonte l'historique complet")
        void goBackMultipleTimes() {
            NavigationHistory.push(player, NavigationHistory.GuiIds.MAIN_MENU);
            NavigationHistory.push(player, NavigationHistory.GuiIds.SECTION);
            NavigationHistory.push(player, NavigationHistory.GuiIds.PURCHASE);

            Entry e1 = NavigationHistory.goBack(player);
            assertEquals(NavigationHistory.GuiIds.SECTION, e1.getGuiId());

            Entry e2 = NavigationHistory.goBack(player);
            assertNotNull(e2);
            assertEquals(NavigationHistory.GuiIds.MAIN_MENU, e2.getGuiId());

            Entry e3 = NavigationHistory.goBack(player);
            assertNull(e3);
            assertEquals(0, NavigationHistory.size(player));
        }
    }

    @Nested
    @DisplayName("peek()")
    class Peek {

        @Test
        @DisplayName("peek retourne null si pas d'historique")
        void peekEmptyReturnsNull() {
            assertNull(NavigationHistory.peek(player));
        }

        @Test
        @DisplayName("peek ne modifie pas l'historique")
        void peekDoesNotModify() {
            NavigationHistory.push(player, NavigationHistory.GuiIds.MAIN_MENU);

            NavigationHistory.peek(player);
            NavigationHistory.peek(player);

            assertEquals(1, NavigationHistory.size(player));
        }
    }

    @Nested
    @DisplayName("canGoBack()")
    class CanGoBack {

        @Test
        @DisplayName("canGoBack false si pas d'historique")
        void canGoBackEmpty() {
            assertFalse(NavigationHistory.canGoBack(player));
        }

        @Test
        @DisplayName("canGoBack false si un seul élément")
        void canGoBackSingle() {
            NavigationHistory.push(player, NavigationHistory.GuiIds.MAIN_MENU);
            assertFalse(NavigationHistory.canGoBack(player));
        }

        @Test
        @DisplayName("canGoBack true si plusieurs éléments")
        void canGoBackStacked() {
            NavigationHistory.push(player, NavigationHistory.GuiIds.MAIN_MENU);
            NavigationHistory.push(player, NavigationHistory.GuiIds.SECTION);
            assertTrue(NavigationHistory.canGoBack(player));
        }
    }

    @Nested
    @DisplayName("replace()")
    class Replace {

        @Test
        @DisplayName("replace sur historique vide se comporte comme push")
        void replaceEmptyActsAsPush() {
            NavigationHistory.replace(player, NavigationHistory.GuiIds.MAIN_MENU);

            assertEquals(1, NavigationHistory.size(player));
            assertEquals(NavigationHistory.GuiIds.MAIN_MENU, NavigationHistory.peek(player).getGuiId());
        }

        @Test
        @DisplayName("replace remplace l'entrée courante sans ajouter de profondeur")
        void replaceKeepsSameDepth() {
            NavigationHistory.push(player, NavigationHistory.GuiIds.MAIN_MENU);
            NavigationHistory.push(player, NavigationHistory.GuiIds.SECTION);

            assertEquals(2, NavigationHistory.size(player));

            NavigationHistory.replace(player, NavigationHistory.GuiIds.HELP);

            assertEquals(2, NavigationHistory.size(player));
            assertEquals(NavigationHistory.GuiIds.HELP, NavigationHistory.peek(player).getGuiId());
        }
    }

    @Nested
    @DisplayName("clear() & clearAll()")
    class Clear {

        @Test
        @DisplayName("clear supprime l'historique d'un joueur")
        void clearRemovesPlayerHistory() {
            NavigationHistory.push(player, NavigationHistory.GuiIds.MAIN_MENU);
            NavigationHistory.clear(player);

            assertEquals(0, NavigationHistory.size(player));
        }

        @Test
        @DisplayName("clearAll supprime tout historique")
        void clearAllRemovesEverything() {
            Player player2 = mock(Player.class);
            when(player2.getUniqueId()).thenReturn(UUID.randomUUID());

            NavigationHistory.push(player, NavigationHistory.GuiIds.MAIN_MENU);
            NavigationHistory.push(player2, NavigationHistory.GuiIds.HELP);

            NavigationHistory.clearAll();

            assertEquals(0, NavigationHistory.size(player));
            assertEquals(0, NavigationHistory.size(player2));
        }
    }

    @Nested
    @DisplayName("getHistory()")
    class GetHistory {

        @Test
        @DisplayName("getHistory retourne une copie")
        void getHistoryReturnsCopy() {
            NavigationHistory.push(player, NavigationHistory.GuiIds.MAIN_MENU);

            Deque<Entry> copy = NavigationHistory.getHistory(player);
            copy.clear();

            assertEquals(1, NavigationHistory.size(player));
        }

        @Test
        @DisplayName("getHistory retourne empty deque si pas d'historique")
        void getHistoryEmptyPlayer() {
            Deque<Entry> history = NavigationHistory.getHistory(player);
            assertNotNull(history);
            assertTrue(history.isEmpty());
        }
    }

    @Nested
    @DisplayName("cleanup()")
    class Cleanup {

        @Test
        @DisplayName("cleanup par UUID supprime l'historique")
        void cleanupByUuid() {
            NavigationHistory.push(player, NavigationHistory.GuiIds.MAIN_MENU);
            NavigationHistory.cleanup(playerId);

            assertEquals(0, NavigationHistory.size(player));
        }
    }

    @Nested
    @DisplayName("Entry")
    class EntryTest {

        @Test
        @DisplayName("Entry stocke guiId, timestamp et contexte")
        void entryFields() {
            Object ctx = "context";
            Entry entry = new Entry("test_gui", ctx);

            assertEquals("test_gui", entry.getGuiId());
            assertNotNull(entry.getContext());
            assertSame(ctx, entry.getContext());
            assertTrue(entry.getTimestamp() > 0);
            assertTrue(System.currentTimeMillis() - entry.getTimestamp() < 1000);
        }

        @Test
        @DisplayName("Entry sans contexte a context null")
        void entryNoContext() {
            Entry entry = new Entry("test_gui");
            assertEquals("test_gui", entry.getGuiId());
            assertNull(entry.getContext());
        }

        @Test
        @DisplayName("Entry toString retourne guiId")
        void entryToString() {
            Entry entry = new Entry("my_gui");
            assertEquals("my_gui", entry.toString());
        }
    }

    @Nested
    @DisplayName("GuiIds")
    class GuiIdsTest {

        @Test
        @DisplayName("Tous les IDs de GUI sont définis et non-null")
        void allGuiIdsDefined() {
            assertNotNull(NavigationHistory.GuiIds.MAIN_MENU);
            assertNotNull(NavigationHistory.GuiIds.SECTION);
            assertNotNull(NavigationHistory.GuiIds.PURCHASE);
            assertNotNull(NavigationHistory.GuiIds.ENCHANT_LEVELS);
            assertNotNull(NavigationHistory.GuiIds.ENCHANT_PURCHASE);
            assertNotNull(NavigationHistory.GuiIds.UTILITY);
            assertNotNull(NavigationHistory.GuiIds.STATS);
            assertNotNull(NavigationHistory.GuiIds.PLAYER_STATS);
            assertNotNull(NavigationHistory.GuiIds.SERVER_STATS);
            assertNotNull(NavigationHistory.GuiIds.ORGANIZATION_STATS);
            assertNotNull(NavigationHistory.GuiIds.LICENSE);
            assertNotNull(NavigationHistory.GuiIds.HELP);
            assertNotNull(NavigationHistory.GuiIds.DOCS);
            assertNotNull(NavigationHistory.GuiIds.STATS_SELECTION);
            assertNotNull(NavigationHistory.GuiIds.RUMOR);
            assertNotNull(NavigationHistory.GuiIds.BLACK_MARKET);
            assertNotNull(NavigationHistory.GuiIds.ADMIN_MAIN);
            assertNotNull(NavigationHistory.GuiIds.ADMIN_SYSTEM);
            assertNotNull(NavigationHistory.GuiIds.ADMIN_ECONOMY);
            assertNotNull(NavigationHistory.GuiIds.ADMIN_SHOPS);
            assertNotNull(NavigationHistory.GuiIds.ADMIN_PLAYERS);
            assertNotNull(NavigationHistory.GuiIds.ADMIN_TRANSACTIONS);
            assertNotNull(NavigationHistory.GuiIds.ADMIN_NOTIFICATIONS);
            assertNotNull(NavigationHistory.GuiIds.ADMIN_STATS);
        }
    }

    @Nested
    @DisplayName("Concurrence")
    class Concurrency {

        @Test
        @DisplayName("push depuis plusieurs threads ne corrompt pas l'état")
        void concurrentPush() throws InterruptedException {
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                threads[i] = new Thread(() ->
                        NavigationHistory.push(player, "gui_" + idx));
            }

            for (Thread t : threads) { t.start(); }
            for (Thread t : threads) { t.join(); }

            assertEquals(threadCount, NavigationHistory.size(player));
        }
    }
}
