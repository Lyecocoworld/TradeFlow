package com.github.lye.gui;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Defines hierarchical relationships between GUIs for navigation.
 * <p>
 * Each GUI has a fixed parent, enabling consistent "back" behavior
 * based on structure rather than visitation history.</p>
 * <p>
 * Example hierarchy:</p>
 * <pre>
 * MainShopGui (root)
 *   ├─ SectionGui
 *   │   └─ PurchaseGui
 *   │   └─ EnchantLevelsGui
 *   │       └─ PurchaseEnchantGui
 *   ├─ UtilityGui
 *   │   ├─ LicenseGui
 *   │   ├─ StatsSelectionGui
 *   │   │   ├─ PlayerStatsGui
 *   │   │   ├─ ServerStatsGui
 *   │   │   └─ OrganizationStatsGui
 *   └─ HelpGui
 *       └─ DocsGui
 * </pre>
 *
 * @author lye
 * @since 0.1
 */
public final class GuiHierarchy {

    private GuiHierarchy() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Hierarchy mapping: GUI ID → Parent GUI ID
     * Root GUIs have null parent.
     */
    private static final Map<String, String> PARENTS;
    static {
        Map<String, String> map = new HashMap<>();
        // === PLAYER GUI HIERARCHY ===

        // Root
        map.put(NavigationHistory.GuiIds.MAIN_MENU, null);

        // Utility subtree
        map.put(NavigationHistory.GuiIds.UTILITY, NavigationHistory.GuiIds.MAIN_MENU);
        map.put(NavigationHistory.GuiIds.LICENSE, NavigationHistory.GuiIds.UTILITY);
        map.put(NavigationHistory.GuiIds.STATS_SELECTION, NavigationHistory.GuiIds.UTILITY);
        map.put(NavigationHistory.GuiIds.PLAYER_STATS, NavigationHistory.GuiIds.STATS_SELECTION);
        map.put(NavigationHistory.GuiIds.SERVER_STATS, NavigationHistory.GuiIds.STATS_SELECTION);
        map.put(NavigationHistory.GuiIds.ORGANIZATION_STATS, NavigationHistory.GuiIds.STATS_SELECTION);

        // Help subtree
        map.put(NavigationHistory.GuiIds.HELP, NavigationHistory.GuiIds.MAIN_MENU);
        map.put(NavigationHistory.GuiIds.DOCS, NavigationHistory.GuiIds.HELP);

        // Shop navigation subtree
        map.put(NavigationHistory.GuiIds.SECTION, NavigationHistory.GuiIds.MAIN_MENU);
        map.put(NavigationHistory.GuiIds.PURCHASE, NavigationHistory.GuiIds.SECTION);
        map.put(NavigationHistory.GuiIds.ENCHANT_LEVELS, NavigationHistory.GuiIds.SECTION);
        map.put(NavigationHistory.GuiIds.ENCHANT_PURCHASE, NavigationHistory.GuiIds.ENCHANT_LEVELS);

        // Special GUIs (back to main)
        map.put(NavigationHistory.GuiIds.RUMOR, NavigationHistory.GuiIds.MAIN_MENU);
        map.put(NavigationHistory.GuiIds.BLACK_MARKET, NavigationHistory.GuiIds.MAIN_MENU);

        // === ADMIN GUI HIERARCHY ===

        // Admin root
        map.put(NavigationHistory.GuiIds.ADMIN_MAIN, null);

        // Admin subtrees
        map.put(NavigationHistory.GuiIds.ADMIN_SYSTEM, NavigationHistory.GuiIds.ADMIN_MAIN);
        map.put(NavigationHistory.GuiIds.ADMIN_ECONOMY, NavigationHistory.GuiIds.ADMIN_MAIN);
        map.put(NavigationHistory.GuiIds.ADMIN_SHOPS, NavigationHistory.GuiIds.ADMIN_MAIN);
        map.put(NavigationHistory.GuiIds.ADMIN_TRANSACTIONS, NavigationHistory.GuiIds.ADMIN_MAIN);
        map.put(NavigationHistory.GuiIds.ADMIN_NOTIFICATIONS, NavigationHistory.GuiIds.ADMIN_MAIN);
        map.put(NavigationHistory.GuiIds.ADMIN_PLAYERS, NavigationHistory.GuiIds.ADMIN_MAIN);
        map.put(NavigationHistory.GuiIds.ADMIN_STATS, NavigationHistory.GuiIds.ADMIN_MAIN);

        PARENTS = Collections.unmodifiableMap(map);
    }

    /**
     * Gets the parent GUI ID for a given GUI ID.
     *
     * @param guiId The GUI ID
     * @return The parent GUI ID, or null if root
     */
    @Nullable
    public static String getParent(@Nullable String guiId) {
        if (guiId == null) {
            return null;
        }
        return PARENTS.get(guiId);
    }

    /**
     * Checks if a GUI is a root (has no parent).
     *
     * @param guiId The GUI ID
     * @return true if root, false otherwise
     */
    public static boolean isRoot(@Nullable String guiId) {
        return guiId != null && PARENTS.containsKey(guiId) && PARENTS.get(guiId) == null;
    }

    /**
     * Checks if a GUI ID is defined in the hierarchy.
     *
     * @param guiId The GUI ID
     * @return true if defined, false otherwise
     */
    public static boolean isDefined(@Nullable String guiId) {
        return guiId != null && PARENTS.containsKey(guiId);
    }

    /**
     * Gets the root GUI ID for a given GUI ID.
     * Traverses up the hierarchy until reaching a root.
     *
     * @param guiId The GUI ID
     * @return The root GUI ID, or null if not found
     */
    @Nullable
    public static String getRoot(@Nullable String guiId) {
        String current = guiId;
        while (current != null && !isRoot(current)) {
            current = getParent(current);
        }
        return current;
    }

    /**
     * Gets the depth of a GUI in the hierarchy (0 = root).
     *
     * @param guiId The GUI ID
     * @return The depth, or -1 if not found
     */
    public static int getDepth(@Nullable String guiId) {
        if (guiId == null) {
            return -1;
        }
        int depth = 0;
        String current = guiId;
        while (current != null && !isRoot(current)) {
            current = getParent(current);
            depth++;
        }
        return current == null ? -1 : depth;
    }
}
