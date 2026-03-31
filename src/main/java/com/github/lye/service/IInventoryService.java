package com.github.lye.service;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

public interface IInventoryService {
    boolean giveItem(Player player, ItemStack item);
    boolean takeItem(Player player, ItemStack item);
    void returnItem(Player player, ItemStack item);
}
