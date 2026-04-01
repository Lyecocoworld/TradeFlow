package com.github.lye.events;

import com.github.lye.TradeFlow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {

    private final TradeFlow plugin;

    public PlayerConnectionListener(TradeFlow plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getGuiNavigator().removeState(event.getPlayer());
    }
}
