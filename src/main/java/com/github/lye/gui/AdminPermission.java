package com.github.lye.gui;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

/**
 * Shared permission guard for all admin GUIs.
 * <p>
 * Every admin click handler must call {@link #check(Player)} before
 * performing any administrative action. This provides defense-in-depth
 * in case a non-admin player somehow obtains access to an admin inventory.</p>
 *
 * @author lye
 * @since 0.1
 */
public final class AdminPermission {

    /** Permission node required for all admin GUI actions. */
    public static final String PERMISSION = "tradeflow.admin";

    private AdminPermission() {}

    /**
     * Checks whether the player has admin permission.
     * <p>
     * If the check fails, the player is sent a denial message and
     * their inventory is closed.</p>
     *
     * @param player the player to check
     * @return {@code true} if the player has permission, {@code false} otherwise
     */
    public static boolean check(Player player) {
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>You don't have permission to do that.</red>"));
            player.closeInventory();
            return false;
        }
        return true;
    }
}
