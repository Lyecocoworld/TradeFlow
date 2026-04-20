package com.github.lye.service.impl;

import com.github.lye.service.IInventoryService;
import com.github.lye.config.settings.IMessageSettings;
import com.github.lye.service.IMessageService;
import com.github.lye.util.FoliaSchedulers;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;

public class DefaultInventoryService implements IInventoryService {

    private final IMessageSettings messageSettings;
    private final IMessageService messageService;
    private final Plugin plugin;

    public DefaultInventoryService(IMessageSettings messageSettings, IMessageService messageService, Plugin plugin) {
        this.messageSettings = messageSettings;
        this.messageService = messageService;
        this.plugin = plugin;
    }

    @Override
    public boolean giveItem(Player player, ItemStack item) {
        PlayerInventory inv = player.getInventory();
        HashMap<Integer, ItemStack> failedItems = inv.addItem(item);

        if (!failedItems.isEmpty()) {
            messageService.sendErrorMessage(player, messageSettings.getNotEnoughSpace(), null);
            // Drop ALL overflow stacks at the player's location via Folia-safe scheduling
            for (ItemStack overflow : failedItems.values()) {
                FoliaSchedulers.run(player, plugin, () -> {
                    if (player.isValid()) {
                        player.getWorld().dropItem(player.getLocation(), overflow);
                    }
                });
            }
            return false;
        }
        return true;
    }

    @Override
    public boolean takeItem(Player player, ItemStack item) {
        PlayerInventory inv = player.getInventory();

        // Snapshot the requested amount before removal mutates the item
        int requested = item.getAmount();

        // removeItem() removes what it can; returns items it could NOT remove
        HashMap<Integer, ItemStack> failedItems = inv.removeItem(item);

        if (!failedItems.isEmpty()) {
            // Sum up everything that couldn't be removed across all returned stacks
            int failedAmount = 0;
            for (ItemStack failed : failedItems.values()) {
                failedAmount += failed.getAmount();
            }
            int actuallyRemoved = requested - failedAmount;

            messageService.sendErrorMessage(player, messageSettings.getNotEnoughItems(), null);

            // Do NOT add items back — whatever wasn't removed is still in the inventory.
            // Return true if at least some items were taken, false if nothing was removed.
            return actuallyRemoved > 0;
        }
        return true;
    }

    @Override
    public void returnItem(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> failed = player.getInventory().addItem(item);
        if (!failed.isEmpty()) {
            // Drop ALL overflow stacks at the player's location via Folia-safe scheduling
            for (ItemStack overflow : failed.values()) {
                FoliaSchedulers.run(player, plugin, () -> {
                    if (player.isValid()) {
                        player.getWorld().dropItem(player.getLocation(), overflow);
                    }
                });
            }
        }
    }
}
