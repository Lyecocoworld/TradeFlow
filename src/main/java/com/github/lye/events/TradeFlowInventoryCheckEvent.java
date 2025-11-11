package com.github.lye.events;

import com.github.lye.data.Database;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import com.github.lye.config.Config;

import com.github.lye.data.Shop;
import com.github.lye.data.ShopUtil;
import com.github.lye.util.Format;

/**
 * The event to check players inventories for items they have auto-sold and
 * to update the collect first settings.
 */
public class TradeFlowInventoryCheckEvent extends TradeFlowEvent {

    public static Map<UUID, List<String>> autosellItemMaxReached = new HashMap<>();
    private final Database database;

    /**
     * Checks a single player's inventory for autosell items
     * and to update collect first settings. This event is intended to be called
     * per-player by a Folia-aware scheduler.
     *
     * @param player The player to check.
     * @param isAsync Whether the event is being run async or not.
     */
    public TradeFlowInventoryCheckEvent(Database database, Player player, boolean isAsync) {
        super(isAsync);
        this.database = database;
        checkInventory(player);
    }

    private void checkInventory(Player player) {
        Database.acquireWriteLock();
        try {
            UUID uuid = player.getUniqueId();
            for (ItemStack item : player.getInventory().getContents()) {

                if (item == null) {
                    continue;
                }

                runUpdate(item, player);

                if (item.getEnchantments().isEmpty()) {
                    continue;
                }

                for (Enchantment enchantment : item.getEnchantments().keySet()) {
                    String name = enchantment.getKey().getKey().toLowerCase();

                    if (!ShopUtil.isInShop(this.database, name)) {
                        continue;
                    }

                    Shop shop = getShop(this.database, name);

                }

            }
        } finally {
            Database.releaseWriteLock();
        }
    }

    private void runUpdate(ItemStack item, @NotNull Player player) {

        String name = item.getType().toString().toLowerCase();

        if (!ShopUtil.isInShop(this.database, name)) {
            return;
        }

        Shop shop = getShop(this.database, name);
        
        UUID uuid = player.getUniqueId();

        boolean autosellEnabled = Config.get().getAutosell().getBoolean(uuid + "." + name, false);

        if (!autosellEnabled) {
            return;
        }

        if (ShopUtil.getSellsLeft(this.database, player, name) - item.getAmount() < 0) {
            if (!autosellItemMaxReached.containsKey(uuid)) {
                List<String> list = autosellItemMaxReached.get(uuid);
                if (list == null) {
                    list = Arrays.asList(name);
                    autosellItemMaxReached.put(uuid, list);
                } else {
                    list.add(name);
                }
            } else {
                List<String> list = autosellItemMaxReached.get(uuid);
                if (!list.contains(name)) {
                    list.add(name);
                    Format.sendMessage(player, Config.get().getRunOutOfSells());
                }
            }
            return;
        }

        int amount = item.getAmount();
        // This fixes a call to a non-existent API method.
        // The entire stack is removed, and the sell limit check should prevent over-selling.
        player.getInventory().removeItem(item);

        shop.addAutosell(uuid, amount);
        shop.addSells(uuid, amount);

    }

    private Shop getShop(Database database, String shopName) {
        if (shopName == null) {
            return null;
        }

        Shop shop = ShopUtil.getShop(database, shopName, true);

        if (shop == null) {
            return null;
        }

        return shop;
    }



}
