package com.github.lye.gui;

import com.github.lye.TradeFlow;
import com.github.lye.util.Format;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/**
 * Standardized navigation bar for all TradeFlow GUIs.
 * <p>
 * Layout for 6-row GUI (54 slots):</p>
 * <pre>
 * ┌─────────────────────────────────────────────────────────┐
 * │  [45]    [46-48]    [49]    [50-52]         [53]        │
 * │  ←       (decor)    Info    (decor)          ✗          │
 * │  Back                          (optional)     Close      │
 * └─────────────────────────────────────────────────────────┘
 * </pre>
 * <p>For smaller GUIs, adjust row accordingly (4-row = slot 35-39, etc)</p>
 *
 * <p>Slot positions by row count:</p>
 * <ul>
 *   <li>6 rows: 45 (back), 49 (info), 53 (close)</li>
 *   <li>5 rows: 36 (back), 40 (info), 44 (close)</li>
 *   <li>4 rows: 27 (back), 31 (info), 35 (close)</li>
 *   <li>3 rows: 18 (back), 22 (info), 26 (close)</li>
 * </ul>
 *
 * @author lye
 * @since 0.1
 */
public class NavigationBar {

    /**
     * Standard slot positions for different GUI sizes.
     */
    public static final class Slots {
        /** 6 rows (54 slots) */
        public static final int ROW_6_BACK = 45;
        public static final int ROW_6_INFO = 49;
        public static final int ROW_6_CLOSE = 53;
        public static final int ROW_6_FORWARD = 51;

        /** 5 rows (45 slots) */
        public static final int ROW_5_BACK = 36;
        public static final int ROW_5_INFO = 40;
        public static final int ROW_5_CLOSE = 44;
        public static final int ROW_5_FORWARD = 42;

        /** 4 rows (36 slots) */
        public static final int ROW_4_BACK = 27;
        public static final int ROW_4_INFO = 31;
        public static final int ROW_4_CLOSE = 35;
        public static final int ROW_4_FORWARD = 33;

        /** 3 rows (27 slots) */
        public static final int ROW_3_BACK = 18;
        public static final int ROW_3_INFO = 22;
        public static final int ROW_3_CLOSE = 26;
        public static final int ROW_3_FORWARD = 24;
    }

    /**
     * Configuration for a navigation bar.
     */
    public static class Config {
        private int rows;
        private String title;
        private Runnable onBack;
        private Runnable onClose;
        private Runnable onForward;
        private boolean showClose = true;
        private boolean showForward = false;

        public Config(int rows) {
            this.rows = rows;
        }

        public Config title(String title) {
            this.title = title;
            return this;
        }

        public Config onBack(Runnable onBack) {
            this.onBack = onBack;
            return this;
        }

        public Config onClose(Runnable onClose) {
            this.onClose = onClose;
            return this;
        }

        public Config onForward(Runnable onForward) {
            this.onForward = onForward;
            this.showForward = true;
            return this;
        }

        public Config showClose(boolean show) {
            this.showClose = show;
            return this;
        }

        public Config showForward(boolean show) {
            this.showForward = show;
            return this;
        }
    }

    /**
     * Applies the navigation bar to a GUI.
     *
     * @param gui   The GUI to add navigation to
     * @param config Navigation configuration
     */
    public static void apply(Gui gui, Config config) {
        int backSlot, infoSlot, closeSlot, forwardSlot;

        // Determine slot positions based on row count
        switch (config.rows) {
            case 6 -> {
                backSlot = Slots.ROW_6_BACK;
                infoSlot = Slots.ROW_6_INFO;
                closeSlot = Slots.ROW_6_CLOSE;
                forwardSlot = Slots.ROW_6_FORWARD;
            }
            case 5 -> {
                backSlot = Slots.ROW_5_BACK;
                infoSlot = Slots.ROW_5_INFO;
                closeSlot = Slots.ROW_5_CLOSE;
                forwardSlot = Slots.ROW_5_FORWARD;
            }
            case 4 -> {
                backSlot = Slots.ROW_4_BACK;
                infoSlot = Slots.ROW_4_INFO;
                closeSlot = Slots.ROW_4_CLOSE;
                forwardSlot = Slots.ROW_4_FORWARD;
            }
            case 3 -> {
                backSlot = Slots.ROW_3_BACK;
                infoSlot = Slots.ROW_3_INFO;
                closeSlot = Slots.ROW_3_CLOSE;
                forwardSlot = Slots.ROW_3_FORWARD;
            }
            default -> {
                backSlot = Slots.ROW_6_BACK;
                infoSlot = Slots.ROW_6_INFO;
                closeSlot = Slots.ROW_6_CLOSE;
                forwardSlot = Slots.ROW_6_FORWARD;
            }
        }

        // Back button (always shown if onBack is provided)
        if (config.onBack != null) {
            ItemStack backItem = createBackButton();
            gui.setItem(backSlot, new GuiItem(backItem, event -> config.onBack.run()));
        }

        // Info/Title button
        if (config.title != null && !config.title.isEmpty()) {
            ItemStack infoItem = createInfoButton(config.title);
            gui.setItem(infoSlot, new GuiItem(infoItem));
        }

        // Forward button (optional)
        if (config.showForward && config.onForward != null) {
            ItemStack forwardItem = createForwardButton();
            gui.setItem(forwardSlot, new GuiItem(forwardItem, event -> config.onForward.run()));
        }

        // Close button (optional)
        if (config.showClose) {
            ItemStack closeItem = createCloseButton();
            gui.setItem(closeSlot, new GuiItem(closeItem, event -> {
                if (config.onClose != null) {
                    config.onClose.run();
                } else {
                    event.getWhoClicked().closeInventory();
                }
            }));
        }

        // Fill empty navigation slots with background
        fillEmptySlots(gui, backSlot, infoSlot, closeSlot, forwardSlot, config.showForward);
    }

    /**
     * Creates a standardized back button.
     */
    public static ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        item.editMeta(meta -> {
            Component displayName = Format.getComponent("navigation-back")
                    .decoration(TextDecoration.ITALIC, false);
            meta.displayName(displayName);
        });
        return item;
    }

    /**
     * Creates a standardized close button.
     */
    public static ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        item.editMeta(meta -> {
            Component displayName = Format.getComponent("navigation-close")
                    .decoration(TextDecoration.ITALIC, false);
            meta.displayName(displayName);
        });
        return item;
    }

    /**
     * Creates a standardized forward/next button.
     */
    public static ItemStack createForwardButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        item.editMeta(meta -> {
            Component displayName = Format.getComponent("navigation-forward")
                    .decoration(TextDecoration.ITALIC, false);
            meta.displayName(displayName);
        });
        return item;
    }

    /**
     * Creates a standardized info/title button.
     */
    public static ItemStack createInfoButton(String text) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.editMeta(meta -> {
            // Use GuiTextCache for standardized gold/bold formatting
            meta.displayName(GuiTextCache.goldBoldTitle(text));
        });
        return item;
    }

    /**
     * Creates a page info button (e.g., "Page 1/3").
     */
    public static ItemStack createPageInfoButton(int current, int total) {
        ItemStack item = new ItemStack(Material.COMPASS);
        item.editMeta(meta -> {
            Component displayName = Format.getComponent("navigation-page",
                            Placeholder.parsed("current", String.valueOf(current)),
                            Placeholder.parsed("total", String.valueOf(total)))
                    .decoration(TextDecoration.ITALIC, false);
            meta.displayName(displayName);
        });
        return item;
    }

    /**
     * Fills empty slots in the navigation bar with background items.
     */
    private static void fillEmptySlots(Gui gui, int backSlot, int infoSlot, int closeSlot,
                                       int forwardSlot, boolean showForward) {
        ItemStack bgItem = gui.getInventory().getItem(0);
        if (bgItem == null || bgItem.getType() == Material.AIR) {
            bgItem = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
            bgItem.editMeta(m -> m.displayName(Component.text(" ")));
        }

        int row = (closeSlot / 9) + 1;
        int startSlot = (row - 1) * 9;
        int endSlot = row * 9;

        for (int i = startSlot; i < endSlot; i++) {
            if (i == backSlot || i == infoSlot || i == closeSlot ||
                (showForward && i == forwardSlot)) {
                continue;
            }
            if (gui.getInventory().getItem(i) == null ||
                gui.getInventory().getItem(i).getType() == Material.AIR) {
                gui.setItem(i, new GuiItem(bgItem));
            }
        }
    }

    /**
     * Quick setup for a "Back to Main" button (returns to main shop).
     */
    public static void backToMain(Gui gui, int rows, Runnable onBack) {
        apply(gui, new Config(rows)
                .title("Menu Principal")
                .onBack(onBack));
    }

    /**
     * Quick setup for a "Back to Parent" button.
     */
    public static void backToParent(Gui gui, int rows, Runnable onBack) {
        apply(gui, new Config(rows)
                .onBack(onBack));
    }

    /**
     * Quick setup for a paginated view.
     */
    public static void paginated(Gui gui, int rows, int currentPage, int totalPages,
                                  Runnable onBack, Runnable onPrevious, Runnable onNext) {
        int backSlot, infoSlot, closeSlot, prevSlot, nextSlot;

        switch (rows) {
            case 6 -> {
                backSlot = Slots.ROW_6_BACK;
                infoSlot = Slots.ROW_6_INFO;
                closeSlot = Slots.ROW_6_CLOSE;
                prevSlot = Slots.ROW_6_BACK + 1;  // 46
                nextSlot = Slots.ROW_6_CLOSE - 1; // 52
            }
            case 5 -> {
                backSlot = Slots.ROW_5_BACK;
                infoSlot = Slots.ROW_5_INFO;
                closeSlot = Slots.ROW_5_CLOSE;
                prevSlot = Slots.ROW_5_BACK + 1;
                nextSlot = Slots.ROW_5_CLOSE - 1;
            }
            case 4 -> {
                backSlot = Slots.ROW_4_BACK;
                infoSlot = Slots.ROW_4_INFO;
                closeSlot = Slots.ROW_4_CLOSE;
                prevSlot = Slots.ROW_4_BACK + 1;
                nextSlot = Slots.ROW_4_CLOSE - 1;
            }
            default -> {
                backSlot = Slots.ROW_6_BACK;
                infoSlot = Slots.ROW_6_INFO;
                closeSlot = Slots.ROW_6_CLOSE;
                prevSlot = Slots.ROW_6_BACK + 1;
                nextSlot = Slots.ROW_6_CLOSE - 1;
            }
        }

        // Back button
        if (onBack != null) {
            gui.setItem(backSlot, new GuiItem(createBackButton(), event -> onBack.run()));
        }

        // Previous button
        if (currentPage > 0) {
            gui.setItem(prevSlot, new GuiItem(createPreviousButton(), event -> onPrevious.run()));
        }

        // Page info
        gui.setItem(infoSlot, new GuiItem(createPageInfoButton(currentPage + 1, totalPages)));

        // Next button
        if (currentPage < totalPages - 1) {
            gui.setItem(nextSlot, new GuiItem(createForwardButton(), event -> onNext.run()));
        }

        // Close button
        gui.setItem(closeSlot, new GuiItem(createCloseButton(), event -> event.getWhoClicked().closeInventory()));
    }

    public static ItemStack createPreviousButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        item.editMeta(meta -> {
            Component displayName = Format.getComponent("navigation-previous")
                    .decoration(TextDecoration.ITALIC, false);
            meta.displayName(displayName);
        });
        return item;
    }
}
