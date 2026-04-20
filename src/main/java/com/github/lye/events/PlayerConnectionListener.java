package com.github.lye.events;

import com.github.lye.TradeFlow;
import com.github.lye.gameplay.rumors.BrokerReputation;
import com.github.lye.gameplay.rumors.RumorManager;
import com.github.lye.gateway.AccessGateway;
import com.github.lye.gui.NavigationHistory;
import com.github.lye.gui.TransactionLock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerConnectionListener implements Listener {

    private final TradeFlow plugin;
    private final AccessGateway accessGateway;

    public PlayerConnectionListener(TradeFlow plugin, AccessGateway accessGateway) {
        this.plugin = plugin;
        this.accessGateway = accessGateway;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        plugin.getServices().get(com.github.lye.gui.GuiNavigator.class).removeState(event.getPlayer());
        NavigationHistory.cleanup(uuid);
        TransactionLock.release(uuid);
        ChestSellSelector.remove(uuid);
        TradeFlowInventoryCheckEvent.remove(uuid);

        if (accessGateway != null) {
            accessGateway.invalidateCache(uuid);
        }

        RumorManager rumorManager = plugin.getServices() != null
                ? plugin.getServices().get(RumorManager.class) : null;
        if (rumorManager != null) {
            BrokerReputation reputation = rumorManager.getReputationManager();
            if (reputation != null) {
                reputation.clearCache(uuid);
            }
        }
    }
}
