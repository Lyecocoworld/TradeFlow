package unprotesting.com.github.events;

import unprotesting.com.github.data.Database;

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
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.CollectFirst;
import unprotesting.com.github.data.CollectFirst.CollectFirstSetting;
import unprotesting.com.github.data.Shop;
import unprotesting.com.github.data.ShopUtil;
import unprotesting.com.github.util.Format;

/**
 * The event tp check players inventories for items they have auto-sold and
 * to update the collect first settings.
 */
public class AutoTuneInventoryCheckEvent extends AutoTuneEvent {

    public static Map<UUID, List<String>> autosellItemMaxReached = new HashMap<>();

    /**
     * Checks a single player's inventory for autosell items
     * and to update collect first settings. This event is intended to be called
     * per-player by a Folia-aware scheduler.
     *
     * @param player The player to check.
     * @param isAsync Whether the event is being run async or not.
     */
    public AutoTuneInventoryCheckEvent(Player player, boolean isAsync) {
        super(isAsync);
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

                    if (!ShopUtil.isInShop(name)) {
                        continue;
                    }

                    Shop shop = getShop(name);
                    updateCf(name, shop, uuid);
                }

            }
        } finally {
            Database.releaseWriteLock();
        }
    }

    private void runUpdate(ItemStack item, @NotNull Player player) {

        String name = item.getType().toString().toLowerCase();

        if (!ShopUtil.isInShop(name)) {
            return;
        }

        Shop shop = getShop(name);
        
        UUID uuid = player.getUniqueId();
        updateCf(name, shop, uuid);
        boolean autosellEnabled = Config.get().getAutosell().getBoolean(uuid + "." + name, false);

        if (!autosellEnabled) {
            return;
        }

        if (ShopUtil.getSellsLeft(player, name) - item.getAmount() < 0) {
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

    private Shop getShop(String shopName) {
        if (shopName == null) {
            return null;
        }

        Shop shop = ShopUtil.getShop(shopName, true);

        if (shop == null) {
            return null;
        }

        return shop;
    }

    private void updateCf(@NotNull String name, @NotNull Shop shop, @NotNull UUID uuid) {

        boolean update = false;
        CollectFirst cf = shop.getSetting();

        if (cf.getSetting().equals(CollectFirstSetting.SERVER)) {
            if (!cf.isFoundInServer()) {
                cf.setFoundInServer(true);
                shop.setSetting(cf);
                update = true;
            }
        } else if (cf.getSetting().equals(CollectFirstSetting.PLAYER)) {
            if (!Database.get().hasPlayerCollected(uuid, name)) {
                Database.get().recordPlayerCollection(uuid, name);
                update = true;
            }
        }

        if (update) {
            ShopUtil.putShop(name, shop);
        }
    }

}
