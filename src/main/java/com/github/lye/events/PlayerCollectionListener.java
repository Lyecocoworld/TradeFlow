package com.github.lye.events;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import com.github.lye.TradeFlow;
import com.github.lye.gateway.AccessGateway;
import com.github.lye.gui.ShopGuiManager;
import com.github.lye.config.ConfigResolver;
import com.github.lye.access.rules.CollectFirstRule.CFMode;

import java.util.Locale;

public class PlayerCollectionListener implements Listener {

    private final TradeFlow plugin;
    private final AccessGateway accessGateway;
    private final ShopGuiManager shopGuiManager;
    private final ConfigResolver configResolver;

    public PlayerCollectionListener(TradeFlow plugin, AccessGateway accessGateway, ShopGuiManager shopGuiManager, ConfigResolver configResolver) {
        this.plugin = plugin;
        this.accessGateway = accessGateway;
        this.shopGuiManager = shopGuiManager;
        this.configResolver = configResolver;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private static String keyOf(ItemStack it) {
        return it.getType().name().toLowerCase(Locale.ROOT);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        String key = keyOf(e.getItem().getItemStack());
        accessGateway.markPlayerCollected(p.getUniqueId(), key);
        maybeMarkServerCollectedIfNeeded(key);
        notifyAndRefreshIfOpen(p, key);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack result = e.getCurrentItem();
        if (result == null || result.getType().isAir()) return;
        String key = keyOf(result);
        accessGateway.markPlayerCollected(p.getUniqueId(), key);
        maybeMarkServerCollectedIfNeeded(key);
        notifyAndRefreshIfOpen(p, key);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack taken = e.getCurrentItem();
        if (e.isShiftClick() && taken != null && taken.getType() != org.bukkit.Material.AIR) {
            String key = keyOf(taken);
            accessGateway.markPlayerCollected(p.getUniqueId(), key);
            maybeMarkServerCollectedIfNeeded(key);
            notifyAndRefreshIfOpen(p, key);
        }
    }

    private void notifyAndRefreshIfOpen(Player p, String itemKey) {
        if (shopGuiManager.isShopOpenFor(p)) {
            p.getScheduler().run(plugin, task -> {
                try {
                    shopGuiManager.refreshFor(p);
                } catch (Throwable t) {
                    plugin.getLogger().warning("GUI refresh failed: " + t.getMessage());
                }
            }, null);
        }
    }

    private void maybeMarkServerCollectedIfNeeded(String key) {
        if (configResolver.resolveCFMode(null, key) == CFMode.SERVER) {
            accessGateway.markServerCollected(key);
        }
    }
}