package com.github.lye.gui.framework;

import dev.triumphteam.gui.guis.BaseGui;
import dev.triumphteam.gui.guis.Gui;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Folia-safe adapter for triumph-gui operations.
 * <p>
 * Wraps {@link Gui#open(Player)} and {@link Gui#update()} calls
 * with region-aware scheduling to prevent crashes on Folia/CanvasMC.
 * On standard Paper, delegates directly to triumph-gui internals.</p>
 *
 * <p><b>Why this exists:</b> triumph-gui 3.1.2 uses {@code Bukkit.getScheduler()}
 * internally, which crashes or causes race conditions on Folia-based servers.
 * This adapter intercepts all GUI operations and routes them through the
 * entity-aware scheduler on Folia, while remaining a no-op passthrough on Paper.</p>
 *
 * <p><b>Thread safety:</b> When called from a navigator that already wraps in
 * {@code player.getScheduler().run()}, this adapter will double-schedule with a
 * 1-tick overhead. This is harmless and keeps all call sites consistent.</p>
 *
 * @author lye
 * @since 0.1
 */
public final class TriumphGuiAdapter {

    private static final boolean IS_FOLIA;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
        IS_FOLIA = folia;
    }

    private TriumphGuiAdapter() {
        // Utility class — no instantiation
    }

    /**
     * Returns whether the server is running Folia.
     *
     * @return {@code true} if Folia/CanvasMC is detected
     */
    public static boolean isFolia() {
        return IS_FOLIA;
    }

    /**
     * Opens a triumph-gui safely on both Paper and Folia.
     * <p>
     * On Folia, schedules the open on the player's region thread via
     * {@code player.getScheduler().run()}. On Paper, delegates directly
     * to {@link Gui#open(Player)}.</p>
     *
     * @param gui    the triumph-gui instance to open
     * @param player the player who will see the GUI
     * @param plugin the plugin instance for scheduling
     */
    public static void openSafe(BaseGui gui, Player player, Plugin plugin) {
        if (IS_FOLIA) {
            player.getScheduler().run(plugin, task -> {
                if (player.isValid() && !player.isDead()) {
                    gui.open(player);
                }
            }, null);
        } else {
            gui.open(player);
        }
    }

    /**
     * Updates a triumph-gui safely on both Paper and Folia.
     * <p>
     * On Folia, schedules the update on the player's region thread.
     * On Paper, delegates directly to {@link Gui#update()}.</p>
     *
     * @param gui    the triumph-gui instance to update
     * @param player the player viewing the GUI (used for region scheduling)
     * @param plugin the plugin instance for scheduling
     */
    public static void updateSafe(BaseGui gui, Player player, Plugin plugin) {
        if (IS_FOLIA) {
            player.getScheduler().run(plugin, task -> {
                if (player.isValid() && !player.isDead()) {
                    gui.update();
                }
            }, null);
        } else {
            gui.update();
        }
    }

    /**
     * Closes the player's inventory safely on both Paper and Folia.
     * <p>
     * On Folia, schedules the close on the player's region thread.
     * On Paper, delegates directly to {@link Player#closeInventory()}.</p>
     *
     * @param player the player whose inventory should close
     * @param plugin the plugin instance for scheduling
     */
    public static void closeSafe(Player player, Plugin plugin) {
        if (IS_FOLIA) {
            player.getScheduler().run(plugin, task -> {
                if (player.isValid() && !player.isDead()) {
                    player.closeInventory();
                }
            }, null);
        } else {
            player.closeInventory();
        }
    }
}
