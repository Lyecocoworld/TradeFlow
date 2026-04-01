package com.github.lye.events;

import com.github.lye.data.Database;
import com.github.lye.data.PurchaseUtil;
import com.github.lye.util.Format;
import com.github.lye.util.PerfMetrics;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.github.lye.service.IMessageService;

/**
 * Lets a player click a chest after pressing Sell to sell items directly from that chest.
 */
public class ChestSellSelector implements Listener {

    private static final Map<UUID, Selection> PENDING = new HashMap<>();

    private final Plugin plugin;
    private final Database database;
    private final PurchaseUtil purchaseUtil;
    private final IMessageService messageService;

    // Static reference used only for the initial instruction message
    private static IMessageService sharedMessageService;

    public ChestSellSelector(Plugin plugin, Database database, PurchaseUtil purchaseUtil, IMessageService messageService) {
        this.plugin = plugin;
        this.database = database;
        this.purchaseUtil = purchaseUtil;
        this.messageService = messageService;
        sharedMessageService = messageService;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static void beginSelection(Player player, String itemName, boolean isEnchant, int level, int quantity) {
        if (quantity <= 0) quantity = 1;
        PENDING.put(player.getUniqueId(), new Selection(itemName, isEnchant, level, quantity));
        if (sharedMessageService != null) {
            sharedMessageService.sendInfoMessage(player, "<gray>Clique droit sur un coffre pour vendre <amount> x <item>.</gray>",
                    net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.resolver(
                            net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.parsed("amount", Integer.toString(quantity)),
                            net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.parsed("item", itemName)
                    ));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = e.getPlayer();
        Selection sel = PENDING.remove(player.getUniqueId());
        if (sel == null) return;

        Block block = e.getClickedBlock();
        if (block == null) return;

        Inventory inv = getInventory(block.getState());
        if (inv == null) {
            messageService.sendErrorMessage(player, "<red>Le bloc sélectionné n'est pas un coffre.</red>", null);
            return;
        }

        int removed = 0;
        if (sel.isEnchant) {
            Enchantment ench = Enchantment.getByKey(NamespacedKey.minecraft(sel.itemName.toLowerCase(Locale.ROOT)));
            if (ench == null) {
                messageService.sendErrorMessage(player, "<red>Enchantement invalide.</red>", null);
                return;
            }
            for (int slot = 0; slot < inv.getSize() && removed < sel.quantity; slot++) {
                ItemStack it = inv.getItem(slot);
                if (it == null || it.getType() != org.bukkit.Material.ENCHANTED_BOOK) continue;
                EnchantmentStorageMeta meta = (EnchantmentStorageMeta) it.getItemMeta();
                if (meta == null) continue;
                Integer lvl = meta.getStoredEnchantLevel(ench);
                if (lvl == null || lvl != sel.level) continue;
                int take = Math.min(it.getAmount(), sel.quantity - removed);
                removed += take;
                it.setAmount(it.getAmount() - take);
                if (it.getAmount() <= 0) inv.setItem(slot, null);
            }
            if (removed > 0) {
                ItemStack sell = new ItemStack(org.bukkit.Material.ENCHANTED_BOOK, removed);
                EnchantmentStorageMeta meta = (EnchantmentStorageMeta) sell.getItemMeta();
                meta.addStoredEnchant(ench, sel.level, true);
                sell.setItemMeta(meta);
                long start = System.nanoTime();
                try {
                    purchaseUtil.sellItemStack(sell, player);
                } finally {
                    PerfMetrics.recordShopOperation(false, System.nanoTime() - start);
                }
            } else {
                messageService.sendInfoMessage(player, "<yellow>Aucun livre enchanté correspondant dans le coffre.</yellow>", null);
            }
        } else {
            org.bukkit.Material mat = org.bukkit.Material.matchMaterial(sel.itemName.toUpperCase(Locale.ROOT));
            if (mat == null) {
                messageService.sendErrorMessage(player, "<red>Item invalide.</red>", null);
                return;
            }
            for (int slot = 0; slot < inv.getSize() && removed < sel.quantity; slot++) {
                ItemStack it = inv.getItem(slot);
                if (it == null || it.getType() != mat) continue;
                int take = Math.min(it.getAmount(), sel.quantity - removed);
                removed += take;
                it.setAmount(it.getAmount() - take);
                if (it.getAmount() <= 0) inv.setItem(slot, null);
            }
            if (removed > 0) {
                ItemStack sell = new ItemStack(mat, removed);
                long start = System.nanoTime();
                try {
                    purchaseUtil.sellItemStack(sell, player);
                } finally {
                    PerfMetrics.recordShopOperation(false, System.nanoTime() - start);
                }
            } else {
                messageService.sendInfoMessage(player, "<yellow>Aucun item correspondant dans le coffre.</yellow>", null);
            }
        }
    }

    private static Inventory getInventory(BlockState state) {
        if (state instanceof InventoryHolder holder) {
            Inventory inv = holder.getInventory();
            if (inv != null) return inv;
        }
        if (state instanceof DoubleChest dc) {
            return dc.getInventory();
        }
        return null;
    }

    private static final class Selection {
        final String itemName;
        final boolean isEnchant;
        final int level;
        final int quantity;
        Selection(String itemName, boolean isEnchant, int level, int quantity) {
            this.itemName = itemName;
            this.isEnchant = isEnchant;
            this.level = level;
            this.quantity = quantity;
        }
    }
}
