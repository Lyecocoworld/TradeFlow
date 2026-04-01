package com.github.lye.gui;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages navigation history for players.
 * <p>
 * Tracks which GUIs a player has visited, allowing proper back navigation.
 * Each player has their own independent history stack.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * NavigationHistory.push(player, "main_menu");
 * NavigationHistory.push(player, "section_gui");
 * String previous = NavigationHistory.goBack(player); // Returns "main_menu"
 * </pre>
 *
 * @author lye
 * @since 0.1
 */
public class NavigationHistory {

    private static final ConcurrentHashMap<UUID, Deque<Entry>> playerHistories = new ConcurrentHashMap<>();

    /**
     * Represents a navigation history entry.
     */
    public static class Entry {
        private final String guiId;
        private final long timestamp;
        private final Object context;

        public Entry(String guiId) {
            this(guiId, null);
        }

        public Entry(String guiId, Object context) {
            this.guiId = guiId;
            this.timestamp = System.currentTimeMillis();
            this.context = context;
        }

        public String getGuiId() {
            return guiId;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public Object getContext() {
            return context;
        }

        @Override
        public String toString() {
            return guiId;
        }
    }

    /**
     * Pushes a new GUI onto the history stack.
     *
     * @param player The player
     * @param guiId  Identifier for the GUI
     */
    public static void push(@NotNull Player player, String guiId) {
        push(player, new Entry(guiId));
    }

    /**
     * Pushes a new entry onto the history stack with context.
     *
     * @param player  The player
     * @param entry   The history entry
     */
    public static void push(@NotNull Player player, Entry entry) {
        Deque<Entry> history = playerHistories.computeIfAbsent(
                player.getUniqueId(),
                k -> new ArrayDeque<>()
        );
        history.push(entry);
    }

    /**
     * Goes back to the previous GUI in history.
     *
     * @param player The player
     * @return The previous GUI entry, or null if no history
     */
    @Nullable
    public static Entry goBack(@NotNull Player player) {
        Deque<Entry> history = playerHistories.get(player.getUniqueId());
        if (history == null || history.isEmpty()) {
            return null;
        }

        // Remove current
        history.pop();

        // Get previous (if exists)
        if (history.isEmpty()) {
            return null;
        }

        return history.peek();
    }

    /**
     * Peeks at the current GUI without removing it.
     *
     * @param player The player
     * @return The current GUI entry, or null if no history
     */
    @Nullable
    public static Entry peek(@NotNull Player player) {
        Deque<Entry> history = playerHistories.get(player.getUniqueId());
        if (history == null || history.isEmpty()) {
            return null;
        }
        return history.peek();
    }

    /**
     * Checks if the player can go back.
     *
     * @param player The player
     * @return true if there's history to go back to
     */
    public static boolean canGoBack(@NotNull Player player) {
        Deque<Entry> history = playerHistories.get(player.getUniqueId());
        return history != null && history.size() > 1;
    }

    /**
     * Clears the navigation history for a player.
     *
     * @param player The player
     */
    public static void clear(@NotNull Player player) {
        playerHistories.remove(player.getUniqueId());
    }

    /**
     * Gets the full history stack for a player.
     *
     * @param player The player
     * @return Copy of the history stack, or empty deque if none
     */
    @NotNull
    public static Deque<Entry> getHistory(@NotNull Player player) {
        Deque<Entry> history = playerHistories.get(player.getUniqueId());
        if (history == null) {
            return new ArrayDeque<>();
        }
        // Return a copy to avoid external modification
        return new ArrayDeque<>(history);
    }

    /**
     * Gets the size of the history stack.
     *
     * @param player The player
     * @return Number of entries in history
     */
    public static int size(@NotNull Player player) {
        Deque<Entry> history = playerHistories.get(player.getUniqueId());
        return history == null ? 0 : history.size();
    }

    /**
     * Replaces the current GUI entry without adding to history depth.
     * Use this when navigating within the same "logical" screen.
     *
     * @param player The player
     * @param guiId  The new GUI ID
     */
    public static void replace(@NotNull Player player, String guiId) {
        replace(player, new Entry(guiId));
    }

    /**
     * Replaces the current entry with context.
     *
     * @param player The player
     * @param entry  The new entry
     */
    public static void replace(@NotNull Player player, Entry entry) {
        Deque<Entry> history = playerHistories.get(player.getUniqueId());
        if (history != null && !history.isEmpty()) {
            history.pop();
        }
        push(player, entry);
    }

    /**
     * Removes history for a disconnected player (cleanup).
     *
     * @param playerId The player's UUID
     */
    public static void cleanup(UUID playerId) {
        playerHistories.remove(playerId);
    }

    /**
     * Standard GUI identifiers for use with navigation history.
     */
    public static final class GuiIds {
        public static final String MAIN_MENU = "main_menu";
        public static final String SECTION = "section";
        public static final String PURCHASE = "purchase";
        public static final String ENCHANT_LEVELS = "enchant_levels";
        public static final String ENCHANT_PURCHASE = "enchant_purchase";
        public static final String UTILITY = "utility";
        public static final String STATS = "stats";
        public static final String PLAYER_STATS = "player_stats";
        public static final String SERVER_STATS = "server_stats";
        public static final String ORGANIZATION_STATS = "organization_stats";
        public static final String LICENSE = "license";
        public static final String HELP = "help";
        public static final String DOCS = "docs";
        public static final String STATS_SELECTION = "stats_selection";
        public static final String RUMOR = "rumor";
        public static final String BLACK_MARKET = "black_market";

        // Admin GUIs
        public static final String ADMIN_MAIN = "admin_main";
        public static final String ADMIN_SYSTEM = "admin_system";
        public static final String ADMIN_ECONOMY = "admin_economy";
        public static final String ADMIN_SHOPS = "admin_shops";
        public static final String ADMIN_PLAYERS = "admin_players";
        public static final String ADMIN_TRANSACTIONS = "admin_transactions";
        public static final String ADMIN_NOTIFICATIONS = "admin_notifications";
        public static final String ADMIN_STATS = "admin_stats";
    }
}
