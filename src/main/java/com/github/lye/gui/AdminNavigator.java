package com.github.lye.gui;

import com.github.lye.TradeFlow;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Navigation manager for admin GUIs.
 * Keeps admin navigation separate from player navigation.
 *
 * @author lye
 * @since 0.1
 */
public class AdminNavigator {

    private final TradeFlow plugin;
    private final ConcurrentHashMap<UUID, AdminGuiState> stateMap = new ConcurrentHashMap<>();

    public AdminNavigator(TradeFlow plugin) {
        this.plugin = plugin;
    }

    public AdminGuiState getState(@NotNull Player player) {
        return stateMap.computeIfAbsent(player.getUniqueId(), uuid -> new AdminGuiState());
    }

    public void removeState(@NotNull Player player) {
        stateMap.remove(player.getUniqueId());
    }

    /**
     * Open the main admin menu.
     */
    public void openMainMenu(@NotNull Player player) {
        AdminMainMenu gui = new AdminMainMenu(plugin, this, player);
        player.getScheduler().run(plugin, task -> gui.open(player), null);
    }

    /**
     * Open the system management GUI (reload, recalculate, etc).
     */
    public void openSystem(@NotNull Player player) {
        AdminSystemGui gui = new AdminSystemGui(plugin, this, player);
        player.getScheduler().run(plugin, task -> gui.open(player), null);
    }

    /**
     * Open the economy management GUI (stats, taxes, events).
     */
    public void openEconomy(@NotNull Player player) {
        AdminEconomyGui gui = new AdminEconomyGui(plugin, this, player);
        player.getScheduler().run(plugin, task -> gui.open(player), null);
    }

    /**
     * Open the shops management GUI.
     */
    public void openShops(@NotNull Player player) {
        AdminShopsGui gui = new AdminShopsGui(plugin, this, player);
        player.getScheduler().run(plugin, task -> gui.open(player), null);
    }

    /**
     * Open the transaction log GUI.
     */
    public void openTransactions(@NotNull Player player) {
        AdminTransactionGui gui = new AdminTransactionGui(plugin, this, player);
        player.getScheduler().run(plugin, task -> gui.open(player), null);
    }

    /**
     * Open the notifications GUI.
     */
    public void openNotifications(@NotNull Player player) {
        AdminNotificationsGui gui = new AdminNotificationsGui(plugin, this, player);
        player.getScheduler().run(plugin, task -> gui.open(player), null);
    }

    /**
     * Open the players management GUI.
     */
    public void openPlayers(@NotNull Player player) {
        AdminPlayersGui gui = new AdminPlayersGui(plugin, this, player);
        player.getScheduler().run(plugin, task -> gui.open(player), null);
    }

    /**
     * State tracker for admin GUI navigation.
     */
    public static class AdminGuiState {
        private String currentScreen = "main";
        private Object contextData;

        public String getCurrentScreen() {
            return currentScreen;
        }

        public void setCurrentScreen(String screen) {
            this.currentScreen = screen;
        }

        public Object getContextData() {
            return contextData;
        }

        public void setContextData(Object data) {
            this.contextData = data;
        }

        public void goToMain() {
            this.currentScreen = "main";
            this.contextData = null;
        }
    }
}
