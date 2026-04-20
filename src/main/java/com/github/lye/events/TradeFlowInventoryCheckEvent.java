package com.github.lye.events;

import com.github.lye.data.Database;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import com.github.lye.config.Config;
import com.github.lye.config.settings.IAutosellSettings;
import com.github.lye.config.settings.IMessageSettings;

import com.github.lye.data.Shop;
import com.github.lye.data.ShopUtil;
import com.github.lye.util.Format;
import com.github.lye.service.IMessageService;

/**
 * The event to check players inventories for items they have auto-sold and
 * to update the collect first settings.
 */
public class TradeFlowInventoryCheckEvent extends TradeFlowEvent {

    public static Map<UUID, List<String>> autosellItemMaxReached = new ConcurrentHashMap<>();

    public static void remove(UUID playerId) {
        autosellItemMaxReached.remove(playerId);
    }

    public static void clearAll() {
        autosellItemMaxReached.clear();
    }

    private final Database database;
    private final ShopUtil shopUtil;
    private final IMessageService messageService;
    private final IAutosellSettings autosellSettings;
    private final IMessageSettings messageSettings;

    /**
     * Checks a single player's inventory for autosell items
     * and to update collect first settings. This event is intended to be called
     * per-player by a Folia-aware scheduler.
     *
     * @param player The player to check.
     * @param isAsync Whether the event is being run async or not.
     */
    public TradeFlowInventoryCheckEvent(Database database, ShopUtil shopUtil, IMessageService messageService,
                                         IAutosellSettings autosellSettings, IMessageSettings messageSettings,
                                         Player player, boolean isAsync) {
        super(isAsync);
        this.database = database;
        this.shopUtil = shopUtil;
        this.messageService = messageService;
        this.autosellSettings = autosellSettings;
        this.messageSettings = messageSettings;
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

                    if (!shopUtil.isInShop(name)) {
                        continue;
                    }

                    Shop shop = shopUtil.getShop(name, true);

                }

            }
        } finally {
            Database.releaseWriteLock();
        }
    }

    private void runUpdate(ItemStack item, @NotNull Player player) {

        String name = item.getType().toString().toLowerCase();

        if (!shopUtil.isInShop(name)) {
            return;
        }

        Shop shop = shopUtil.getShop(name, true);
        
        UUID uuid = player.getUniqueId();

        boolean autosellEnabled = autosellSettings.getAutosell().getBoolean(uuid + "." + name, false);

        if (!autosellEnabled) {
            return;
        }

        if (shopUtil.getSellsLeft(player, name) - item.getAmount() < 0) {
            List<String> list = autosellItemMaxReached.computeIfAbsent(uuid, k -> new ArrayList<>());
            if (!list.contains(name)) {
                list.add(name);
                messageService.sendInfoMessage(player, messageSettings.getRunOutOfSells(), null);
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

}
