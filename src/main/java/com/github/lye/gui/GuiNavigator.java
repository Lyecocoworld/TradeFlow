package com.github.lye.gui;

import com.github.lye.TradeFlow;
import com.github.lye.gui.state.PlayerShopState;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Navigator for player GUIs with history tracking.
 * <p>
 * Manages navigation between shop GUIs and tracks history
 * for proper back button behavior.</p>
 *
 * @author lye
 * @since 0.1
 */
public class GuiNavigator {

    private final TradeFlow plugin;
    private final ConcurrentHashMap<UUID, PlayerShopState> stateMap = new ConcurrentHashMap<>();

    public GuiNavigator(TradeFlow plugin) {
        this.plugin = plugin;
    }

    public PlayerShopState getState(@NotNull Player player) {
        return stateMap.computeIfAbsent(player.getUniqueId(), PlayerShopState::new);
    }

    public void removeState(@NotNull Player player) {
        cleanup(player);
    }

    // ==================== MAIN NAVIGATION ====================

    /**
     * Opens the main shop GUI.
     * Uses zMenu when available, falls back to triumph-gui otherwise.
     */
    public void openMain(@NotNull Player player) {
        PlayerShopState state = getState(player);
        state.goToMain();

        // Set current GUI (hierarchical navigation)
        setCurrentGui(player, NavigationHistory.GuiIds.MAIN_MENU);

        MainShopGui gui = new MainShopGui(plugin, this, state, plugin.getTradeLogger());
        player.getScheduler().run(plugin, task -> gui.open(player), null);
    }

    // ==================== HIERARCHICAL NAVIGATION STATE ====================

    private final ConcurrentHashMap<UUID, String> currentGuis = new ConcurrentHashMap<>();

    /**
     * Sets the current GUI ID for a player.
     */
    private void setCurrentGui(@NotNull Player player, @NotNull String guiId) {
        currentGuis.put(player.getUniqueId(), guiId);
    }

    /**
     * Gets the current GUI ID for a player.
     */
    @Nullable
    private String getCurrentGui(@NotNull Player player) {
        return currentGuis.get(player.getUniqueId());
    }

    /**
     * Opens a section GUI.
     * Uses zMenu when available, falls back to triumph-gui otherwise.
     */
    public void openSection(@NotNull Player player, @NotNull String sectionId) {
        PlayerShopState state = getState(player);
        state.goToSection(sectionId);

        // Store context for hierarchical back navigation
        setCurrentGui(player, NavigationHistory.GuiIds.SECTION);
        setSectionContext(player, sectionId);

        SectionGui gui = new SectionGui(plugin, this, state, player, plugin.getTradeLogger(), plugin.getShopUtil(), plugin.getMessageService());
        player.getScheduler().run(plugin, task -> gui.open(player), null);
    }

    /**
     * Opens the purchase GUI for an item.
     * Uses zMenu when available, falls back to triumph-gui otherwise.
     */
    public void openPurchase(@NotNull Player player, @NotNull String itemId) {
        PlayerShopState state = getState(player);
        state.goToPurchase(itemId);

        // Store context for hierarchical back navigation
        setCurrentGui(player, NavigationHistory.GuiIds.PURCHASE);
        setItemContext(player, itemId);

        PurchaseGui gui = new PurchaseGui(plugin, this, state, player);
        player.getScheduler().run(plugin, task -> gui.open(player), null);
    }

    /**
     * Opens the enchant levels GUI.
     */
    public void openEnchantLevels(@NotNull Player player, @NotNull String enchantShopName) {
        PlayerShopState state = getState(player);
        state.goToEnchantLevels(enchantShopName);

        // Store context for hierarchical back navigation
        setCurrentGui(player, NavigationHistory.GuiIds.ENCHANT_LEVELS);
        setEnchantContext(player, enchantShopName);

        EnchantLevelsGui gui = new EnchantLevelsGui(plugin, this, state, player);
        player.getScheduler().run(plugin, task -> gui.open(player), null);
    }

    /**
     * Opens the enchant purchase GUI.
     */
    public void openPurchaseEnchant(@NotNull Player player, @NotNull String enchantShopName, int level) {
        PlayerShopState state = getState(player);
        state.goToPurchaseEnchant(enchantShopName, level);

        // Store context for hierarchical back navigation
        setCurrentGui(player, NavigationHistory.GuiIds.ENCHANT_PURCHASE);
        setEnchantContext(player, enchantShopName);
        setEnchantLevel(player, level);

        PurchaseEnchantGui gui = new PurchaseEnchantGui(plugin, this, state, player);
        player.getScheduler().run(plugin, task -> gui.open(player), null);
    }

    // ==================== CONTEXT STORAGE ====================

    private final ConcurrentHashMap<UUID, String> sectionContexts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> itemContexts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> enchantContexts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> enchantLevels = new ConcurrentHashMap<>();

    private void setSectionContext(@NotNull Player player, @Nullable String sectionId) {
        if (sectionId != null) {
            sectionContexts.put(player.getUniqueId(), sectionId);
        }
    }

    @Nullable
    private String getSectionContext(@NotNull Player player) {
        return sectionContexts.get(player.getUniqueId());
    }

    private void setItemContext(@NotNull Player player, @Nullable String itemId) {
        if (itemId != null) {
            itemContexts.put(player.getUniqueId(), itemId);
        }
    }

    @Nullable
    private String getItemContext(@NotNull Player player) {
        return itemContexts.get(player.getUniqueId());
    }

    private void setEnchantContext(@NotNull Player player, @Nullable String enchantName) {
        if (enchantName != null) {
            enchantContexts.put(player.getUniqueId(), enchantName);
        }
    }

    @Nullable
    private String getEnchantContext(@NotNull Player player) {
        return enchantContexts.get(player.getUniqueId());
    }

    private void setEnchantLevel(@NotNull Player player, int level) {
        enchantLevels.put(player.getUniqueId(), level);
    }

    @Nullable
    private Integer getEnchantLevel(@NotNull Player player) {
        return enchantLevels.get(player.getUniqueId());
    }

    /**
     * Goes back to the parent GUI based on hierarchy.
     * Uses GuiHierarchy to determine the parent, not visitation history.
     *
     * @return true if went back, false if already at root
     */
    public boolean goBack(@NotNull Player player) {
        String currentGui = getCurrentGui(player);
        if (currentGui == null) {
            // No current GUI, go to main
            openMain(player);
            return true;
        }

        // Get parent from hierarchy
        String parentGui = GuiHierarchy.getParent(currentGui);

        if (parentGui == null) {
            // Already at root, can't go back
            return false;
        }

        // Navigate to parent using stored context
        return navigateToGui(player, parentGui);
    }

    /**
     * Navigates to a specific GUI ID using stored context.
     */
    private boolean navigateToGui(@NotNull Player player, @NotNull String guiId) {
        return switch (guiId) {
            case NavigationHistory.GuiIds.MAIN_MENU -> {
                openMain(player);
                yield true;
            }
            case NavigationHistory.GuiIds.SECTION -> {
                String sectionId = getSectionContext(player);
                if (sectionId != null) {
                    openSection(player, sectionId);
                    yield true;
                }
                openMain(player);
                yield true;
            }
            case NavigationHistory.GuiIds.PURCHASE -> {
                String itemId = getItemContext(player);
                if (itemId != null) {
                    openPurchase(player, itemId);
                    yield true;
                }
                // No item context, go back to section
                String sectionId = getSectionContext(player);
                if (sectionId != null) {
                    openSection(player, sectionId);
                    yield true;
                }
                openMain(player);
                yield true;
            }
            case NavigationHistory.GuiIds.ENCHANT_LEVELS -> {
                String enchantName = getEnchantContext(player);
                if (enchantName != null) {
                    openEnchantLevels(player, enchantName);
                    yield true;
                }
                String sectionId = getSectionContext(player);
                if (sectionId != null) {
                    openSection(player, sectionId);
                    yield true;
                }
                openMain(player);
                yield true;
            }
            default -> {
                // Unknown GUI, go to main
                openMain(player);
                yield true;
            }
        };
    }

    /**
     * Checks if player can go back (not at root).
     */
    public boolean canGoBack(@NotNull Player player) {
        String currentGui = getCurrentGui(player);
        if (currentGui == null) {
            return false;
        }
        String parent = GuiHierarchy.getParent(currentGui);
        return parent != null;
    }

    /**
     * Cleans up navigation state for a player.
     */
    public void cleanup(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        stateMap.remove(uuid);
        currentGuis.remove(uuid);
        sectionContexts.remove(uuid);
        itemContexts.remove(uuid);
        enchantContexts.remove(uuid);
        enchantLevels.remove(uuid);
    }

    // ==================== COMPATIBILITY METHODS ====================

    public void goToMain(@NotNull Player player) {
        openMain(player);
    }

    public void goToSection(@NotNull Player player, @NotNull String sectionName) {
        openSection(player, sectionName);
    }

    public void goToPurchase(@NotNull Player player, @NotNull String itemName) {
        openPurchase(player, itemName);
    }

    public void goToEnchantLevels(@NotNull Player player, @NotNull String enchantName) {
        openEnchantLevels(player, enchantName);
    }

    public void goToPurchaseEnchant(@NotNull Player player, @NotNull String enchantName, int level) {
        openPurchaseEnchant(player, enchantName, level);
    }

    // ==================== CONTEXT OBJECTS ====================

    /**
     * Context for enchant navigation.
     */
    public static class EnchantContext {
        private final String enchantName;
        private final int level;

        public EnchantContext(String enchantName, int level) {
            this.enchantName = enchantName;
            this.level = level;
        }

        public String getEnchantName() {
            return enchantName;
        }

        public int getLevel() {
            return level;
        }
    }
}
