package unprotesting.com.github.events;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import unprotesting.com.github.AutoTune;
import unprotesting.com.github.data.CollectFirst;
import unprotesting.com.github.data.Database;
import unprotesting.com.github.data.Shop;
import unprotesting.com.github.data.ShopUtil;

public class PlayerCollectionListener implements Listener {

    public PlayerCollectionListener(AutoTune plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        // Operators should bypass collection requirements, not fulfill them automatically.
        if (player.isOp()) {
            return;
        }

        ItemStack item = event.getItem().getItemStack();
        String itemName = item.getType().toString().toLowerCase();

        // --- Perform cheap, read-only checks first ---
        Shop shop;
        Database.acquireReadLock();
        try {
            shop = ShopUtil.getShop(itemName, false);
        } finally {
            Database.releaseReadLock();
        }

                // Exit early if the item is not in a shop or the feature is disabled for it.

                if (shop == null || shop.getSetting().getSetting() == CollectFirst.CollectFirstSetting.NONE) {

                    return;

                }

        

                CollectFirst cf = shop.getSetting();

        

                // Check if an update is even needed before acquiring an expensive write lock

                boolean needsUpdate = (cf.getSetting() == CollectFirst.CollectFirstSetting.SERVER && !cf.isFoundInServer()) ||

                                      (cf.getSetting() == CollectFirst.CollectFirstSetting.PLAYER && !Database.get().hasPlayerCollected(player.getUniqueId(), itemName));

        if (!needsUpdate) {
            return;
        }

        // --- Acquire write lock only when we are certain we need to modify data ---
        Database.acquireWriteLock();
        try {
            // Re-fetch the shop and setting objects inside the write lock to ensure we have the latest data
            shop = ShopUtil.getShop(itemName, false);
            if (shop == null) return; // Safeguard
            
            cf = shop.getSetting();
            boolean updated = false;

            if (cf.getSetting() == CollectFirst.CollectFirstSetting.SERVER) {
                if (!cf.isFoundInServer()) {
                    cf.setFoundInServer(true);
                    updated = true;
                }
            } else if (cf.getSetting() == CollectFirst.CollectFirstSetting.PLAYER) {
                if (!Database.get().hasPlayerCollected(player.getUniqueId(), itemName)) {
                    Database.get().recordPlayerCollection(player.getUniqueId(), itemName);
                    updated = true;
                }
            }

            if (updated) {
                shop.setSetting(cf);
                ShopUtil.putShop(itemName, shop);
            }
        } finally {
            Database.releaseWriteLock();
        }
    }
}
