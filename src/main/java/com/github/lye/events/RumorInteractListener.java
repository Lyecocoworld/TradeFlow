package com.github.lye.events;

import com.github.lye.TradeFlow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import com.github.lye.gameplay.rumors.RumorManager;

public class RumorInteractListener implements Listener {

    private final TradeFlow plugin;
    private final RumorManager rumorManager;

    public RumorInteractListener(TradeFlow plugin) {
        this.plugin = plugin;
        this.rumorManager = plugin.getRumorManager();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null) return;

        if (rumorManager.isRumorItem(item)) {
            event.setCancelled(true);
            rumorManager.revealRumor(event.getPlayer(), item);
        }
    }
}
