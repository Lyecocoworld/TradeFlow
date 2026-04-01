package com.github.lye.service.impl;

import com.github.lye.service.IInventoryService;
import com.github.lye.config.settings.IMessageSettings;
import com.github.lye.service.IMessageService;
import com.github.lye.util.Format;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;

public class DefaultInventoryService implements IInventoryService {

    private final IMessageSettings messageSettings;
    private final IMessageService messageService;

    public DefaultInventoryService(IMessageSettings messageSettings, IMessageService messageService) {
        this.messageSettings = messageSettings;
        this.messageService = messageService;
    }

    @Override
    public boolean giveItem(Player player, ItemStack item) {
        PlayerInventory inv = player.getInventory();
        HashMap<Integer, ItemStack> failedItems = inv.addItem(item);

        if (!failedItems.isEmpty()) {
            // Assuming TagResolver 'r' would be passed or created here if needed for the message
            messageService.sendErrorMessage(player, messageSettings.getNotEnoughSpace(), null);
            player.getWorld().dropItem(player.getLocation(), failedItems.get(0));
            return false;
        }
        return true;
    }

    @Override
    public boolean takeItem(Player player, ItemStack item) {
        PlayerInventory inv = player.getInventory();
        HashMap<Integer, ItemStack> failedItems = inv.removeItem(item);

        if (!failedItems.isEmpty()) {
            // Assuming TagResolver 'r' would be passed or created here if needed for the message
            messageService.sendErrorMessage(player, messageSettings.getNotEnoughItems(), null);
            // Return the items that couldn't be removed
            ItemStack returned = failedItems.get(0);
            returned.setAmount(item.getAmount() - returned.getAmount());
            inv.addItem(returned);
            return false;
        }
        return true;
    }

    @Override
    public void returnItem(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> failed = player.getInventory().addItem(item);
        if (!failed.isEmpty()) {
            player.getWorld().dropItem(player.getLocation(), failed.get(0));
        }
    }
}
