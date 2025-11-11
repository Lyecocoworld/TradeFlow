package unprotesting.com.github.data;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.Transaction.TransactionType;
import unprotesting.com.github.util.EconomyUtil;
import unprotesting.com.github.util.Format;

import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A utility class for purchasing and selling items.
 */
public class PurchaseUtil {

    private static final ConcurrentHashMap<UUID, Object> playerLocks = new ConcurrentHashMap<>();

    /**
     * Purchase/sell an item from a shop.
     *
     * @param name   The name of the shop.
     * @param player The player uuid.
     * @param amount The item to purchase.
     */
    public static void purchaseItem(String name, Player player, int amount, boolean isBuy) {
        Format.getLog().info(String.format("[DEBUG] purchaseItem called for %s, player %s, amount %d, isBuy %b", name, player.getName(), amount, isBuy));
        if (amount <= 0) {
            Format.sendRawMessage(player, "<red>Amount must be positive.</red>");
            Format.getLog().info("[DEBUG] purchaseItem: Amount <= 0");
            return;
        }

        final Object playerLock = playerLocks.computeIfAbsent(player.getUniqueId(), k -> new Object());
        synchronized (playerLock) {
            Shop shop = getAssociatedShop(player, name);
            if (shop == null) {
                Format.getLog().info("[DEBUG] purchaseItem: Shop is null");
                return;
            }

            // --- Global Stock Limit Logic (for Sells) ---
            if (!isBuy && shop.getGlobalStockLimit() > 0) {
                Format.getLog().info("[DEBUG] purchaseItem: Checking global stock limit for sell");
                int remainingStock = unprotesting.com.github.AutoTune.getInstance().getGlobalStockManager().getRemainingStock(shop);
                if (remainingStock <= 0) {
                    Format.sendRawMessage(player, "<red>The server's demand for this item has been met for this period. Please try again later.</red>");
                    Format.getLog().info("[DEBUG] purchaseItem: Remaining stock <= 0");
                    return;
                }
                if (amount > remainingStock) {
                    // TODO: Add a specific message for this to messages.yml
                    TagResolver stockResolver = TagResolver.resolver(Placeholder.parsed("amount", String.valueOf(remainingStock)), Placeholder.parsed("limit", String.valueOf(amount)));
                    Format.sendRawMessage(player, "<yellow>The server's demand is limited. Only <amount>/<limit> items were sold.</yellow>", stockResolver);
                    amount = remainingStock;
                    Format.getLog().info("[DEBUG] purchaseItem: Amount adjusted due to remaining stock");
                }
            }
            // --- End of Global Stock Limit Logic ---

            Component display = Shop.getDisplayName(name, shop.isEnchantment());
            double price = isBuy ? shop.getPrice() : shop.getSellPrice();
            double total = price * amount;

            if (total < 0) {
                Format.sendRawMessage(player, "<red>Invalid transaction: Total price cannot be negative.</red>");
                Format.getLog().info("[DEBUG] purchaseItem: Total price < 0");
                return;
            }

            double balance = EconomyUtil.getEconomy().getBalance(player);
            TagResolver r = getTagResolver(display, price, amount, balance, shop.getSetting());
            UUID uuid = player.getUniqueId();
            Config config = Config.get();

            if (isBuy && !player.isOp() && !shop.isUnlocked(uuid)) {
                Format.sendRawMessage(player, config.getNotUnlocked(), r);
                Format.getLog().info("[DEBUG] purchaseItem: Shop not unlocked");
                return;
            }

            if (isBuy && balance < total) {
                Format.sendRawMessage(player, config.getNotEnoughMoney(), r);
                Format.getLog().info("[DEBUG] purchaseItem: Not enough money");
                return;
            }

            if (Config.get().isEnableSellLimits()
                    && Database.get().getPurchasesLeft(name, uuid, isBuy) - amount < 0) {
                if (isBuy) {
                    Format.sendRawMessage(player, config.getRunOutOfBuys(), r);
                } else {
                    Format.sendRawMessage(player, config.getRunOutOfSells(), r);
                }
                Format.getLog().info("[DEBUG] purchaseItem: Sell limits reached");
                return;
            }

            boolean success = shop.isEnchantment() ? enchant(player, name, amount, isBuy, r)
                    : item(player, name, amount, isBuy, r);

            if (!success) {
                Format.getLog().info("[DEBUG] purchaseItem: item/enchant method returned false");
                return;
            }

            Format.getLog().info("[DEBUG] purchaseItem: Transaction successful, processing...");
            TransactionType position = isBuy ? TransactionType.BUY : TransactionType.SELL;
            Transaction transaction = new Transaction(price, amount, uuid, name, position);
            Database.get().putTransaction(java.util.UUID.randomUUID().toString(), transaction);
            EconomyDataUtil.increaseEconomyData("GDP", total / 2);

            if (isBuy) {
                shop.addBuys(uuid, amount);
            } else {
                shop.addSells(uuid, amount);
            }

            if (isBuy) {
                EconomyUtil.getEconomy().withdrawPlayer(player, total);
            } else {
                EconomyUtil.getEconomy().depositPlayer(player, total);
            }

            String message = isBuy ? config.getShopPurchase() : config.getShopSell();
            Format.sendRawMessage(player, message, r);
            double loss = shop.getPrice() * amount - total;
            EconomyDataUtil.increaseEconomyData("LOSS", loss);

            // Record sale in global stock manager
            if (!isBuy) {
                unprotesting.com.github.AutoTune.getInstance().getGlobalStockManager().recordSale(shop, amount);
            }

            ShopUtil.putShop(name, shop);
            Format.getLog().info("[DEBUG] purchaseItem: Transaction completed.");
            // Le nouveau système de pricing met à jour les prix automatiquement, pas besoin de mise à jour manuelle ici.
        }
    }

    /**
     * Sell an item stack to all relevant shops.
     *
     * @param item   The item stack to sell.
     * @param player The player object.
     */
    public static void sellItemStack(ItemStack item, Player player) {
        if (item.getAmount() <= 0) {
            return; // Nothing to sell
        }

        final Object playerLock = playerLocks.computeIfAbsent(player.getUniqueId(), k -> new Object());
        synchronized (playerLock) {
            int amount = item.getAmount();
            boolean success = true;
            double total = 0;
            double balance = EconomyUtil.getEconomy().getBalance(player);
            UUID uuid = player.getUniqueId();
            TagResolver r = getTagResolver(item.displayName(), total / amount, amount, balance, null);
            Config config = Config.get();

            for (Enchantment enchantment : item.getEnchantments().keySet()) {
                String enchantmentName = enchantment.getKey().getKey();
                Shop shop = getAssociatedShop(player, enchantmentName);
                if (shop == null) {
                    Format.sendRawMessage(player, config.getNotInShop(), r);
                    success = false;
                    break;
                }

                double price = shop.getSellPrice() * item.getEnchantmentLevel(enchantment);
                price = scalePriceToDurability(item, price);
                total += price * amount;
                r = getTagResolver(item.displayName(), price, amount, balance, null);

                if (config.isEnableSellLimits()
                        && ShopUtil.getSellsLeft(player, enchantmentName) - amount < 0) {
                    Format.sendRawMessage(player, config.getRunOutOfSells(), r);
                    success = false;
                    break;
                }

            }

            String itemName = item.getType().toString().toLowerCase();
            Shop itemShop = ShopUtil.getShop(itemName, true);
            if (itemShop == null) {
                Format.sendRawMessage(player, config.getNotInShop(), r);
                returnItem(player, item);
                return;
            }

            double price = itemShop.getSellPrice();
            price = scalePriceToDurability(item, price);

            if (price == 0) {
                Format.sendRawMessage(player, config.getNotInShop(), r);
                returnItem(player, item);
                return;
            }

            // --- Global Stock Limit Logic (for Sells) ---
            if (itemShop.getGlobalStockLimit() > 0) {
                int remainingStock = unprotesting.com.github.AutoTune.getInstance().getGlobalStockManager().getRemainingStock(itemShop);
                if (remainingStock <= 0) {
                    Format.sendRawMessage(player, "<red>The server's demand for this item has been met for this period. Please try again later.</red>");
                    returnItem(player, item);
                    return;
                }
                if (amount > remainingStock) {
                    TagResolver stockResolver = TagResolver.resolver(Placeholder.parsed("amount", String.valueOf(remainingStock)), Placeholder.parsed("limit", String.valueOf(amount)));
                    Format.sendRawMessage(player, "<yellow>The server's demand is limited. Only <amount>/<limit> items were sold.</yellow>", stockResolver);
                    // Adjust the amount for the transaction and return the rest to the player
                    int amountToReturn = amount - remainingStock;
                    amount = remainingStock;
                    item.setAmount(amountToReturn);
                    returnItem(player, item);
                }
            }
            // --- End of Global Stock Limit Logic ---

            total += price * amount;
            r = getTagResolver(item.displayName(), price, amount, balance, null);

            if (config.isEnableSellLimits() && ShopUtil.getSellsLeft(player, itemName) - amount < 0) {
                Format.sendRawMessage(player, config.getRunOutOfSells(), r);
                success = false;
            }

            r = getTagResolver(item.displayName(), total / amount, amount, balance, null);

            if (!success) {
                returnItem(player, item);
                return;
            }

            for (Enchantment enchantment : item.getEnchantments().keySet()) {
                String name = enchantment.getKey().getKey();
                Shop shop = getAssociatedShop(player, name);
                createTransaction(amount, total, uuid, name, shop, price);
            }

            createTransaction(amount, total, uuid, itemName, itemShop, price);
            EconomyUtil.getEconomy().depositPlayer(player, total);
            unprotesting.com.github.AutoTune.getInstance().getGlobalStockManager().recordSale(itemShop, amount);
            Format.sendRawMessage(player, config.getShopSell(), r);
        }

    }

    public static void purchaseEnchantment(Player player, String name, int level, int quantity) {
        if (quantity <= 0) {
            Format.sendRawMessage(player, "<red>Quantity must be positive.</red>");
            return;
        }

        final Object playerLock = playerLocks.computeIfAbsent(player.getUniqueId(), k -> new Object());
        synchronized (playerLock) {
            Shop shop = getAssociatedShop(player, name);
            if (shop == null) {
                return;
            }

            Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(name));
            if (enchantment == null) {
                return; // Should not happen
            }

            double price = shop.getPrice() * level;
            double total = price * quantity;

            if (total < 0) {
                Format.sendRawMessage(player, "<red>Invalid transaction: Total price cannot be negative.</red>");
                return;
            }

            double balance = EconomyUtil.getEconomy().getBalance(player);
            if (balance < total) {
                Format.sendRawMessage(player, Config.get().getNotEnoughMoney());
                return;
            }

            ItemStack enchantedBook = new ItemStack(Material.ENCHANTED_BOOK, quantity);
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) enchantedBook.getItemMeta();
            meta.addStoredEnchant(enchantment, level, true);
            enchantedBook.setItemMeta(meta);

            HashMap<Integer, ItemStack> failedItems = player.getInventory().addItem(enchantedBook);

            if (!failedItems.isEmpty()) {
                Format.sendRawMessage(player, Config.get().getNotEnoughSpace());
                player.getWorld().dropItem(player.getLocation(), failedItems.get(0));
            }

            EconomyUtil.getEconomy().withdrawPlayer(player, total);

            Transaction transaction = new Transaction(price, quantity, player.getUniqueId(), name, Transaction.TransactionType.BUY);
            Database.get().putTransaction(java.util.UUID.randomUUID().toString(), transaction);
            shop.addBuys(player.getUniqueId(), quantity);
            ShopUtil.putShop(name, shop);

            Format.sendRawMessage(player, Config.get().getShopPurchase());
        }
    }

    public static void returnItem(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> failed = player.getInventory().addItem(item);
        if (!failed.isEmpty()) {
            player.getWorld().dropItem(player.getLocation(), failed.get(0));
        }
    }

    private static void createTransaction(int amount, double total, UUID uuid, String itemName,
            Shop itemShop, double price) {
        Transaction transaction = new Transaction(
                price, amount, uuid, itemName, TransactionType.SELL);
        Database.get().transactions.put(java.util.UUID.randomUUID().toString(), transaction);
        EconomyDataUtil.increaseEconomyData("GDP", total / 2);
        double loss = itemShop.getPrice() - itemShop.getSellPrice();
        EconomyDataUtil.increaseEconomyData("LOSS", loss * amount);
        itemShop.addSells(uuid, amount);
        ShopUtil.putShop(itemName, itemShop);
    }

    public static TagResolver getTagResolver(Component display, double price,
            int amount, double balance, CollectFirst cf) {
        TagResolver.Builder builder = TagResolver.builder();
        builder.resolver(TagResolver.resolver(
                Placeholder.component("item", display),
                Placeholder.parsed("total", Format.currency(price * amount)),
                Placeholder.parsed("price", Format.currency(price)),
                Placeholder.parsed("amount", Integer.toString(amount)),
                Placeholder.parsed("balance", Format.currency(balance))));
        if (cf != null) {
            builder.resolver(Placeholder.parsed("collect-first-setting",
                    cf.getSetting().toString().toLowerCase()));
        }
        return builder.build();
    }

    private static Shop getAssociatedShop(Player player, String itemName) {
        if (itemName == null) {
            return null;
        }
        String name = itemName.toLowerCase();
        Shop shop = ShopUtil.getShop(name, true);
        if (shop == null) {
            Format.sendRawMessage(player, Config.get().getNotInShop());
            return null;
        }

        return shop;
    }

    private static boolean item(Player player, String name, int amount,
            boolean isBuy, TagResolver r) {
        PlayerInventory inv = player.getInventory();
        Material material = Material.matchMaterial(name);
        if (material == null) {
            material = Material.BARRIER; // Fallback to BARRIER if material not found
        }
        ItemStack item = new ItemStack(material, amount);
        HashMap<Integer, ItemStack> map = isBuy ? inv.addItem(item) : inv.removeItem(item);

        if (scalePriceToDurability(item, 1) == 0 && !isBuy) {
            inv.addItem(item);
            Format.sendRawMessage(player, Config.get().getNotInShop(), r);
            return false;
        }

        if (map.isEmpty()) {
            return true;
        }

        if (!isBuy) {
            ItemStack returned = map.get(0);
            returned.setAmount(amount - returned.getAmount());
            inv.addItem(returned);
            Format.sendRawMessage(player, Config.get().getNotEnoughItems(), r);
            return false;
        }

        Format.sendRawMessage(player, Config.get().getNotEnoughSpace(), r);
        player.getWorld().dropItem(player.getLocation(), map.get(0));
        return true;
    }

    private static boolean enchant(Player player, String name, int amount, boolean isBuy, TagResolver r) {
        // This method now only handles BUYING enchantments as books.
        // Selling enchanted items is handled by sellItemStack.
        if (!isBuy) {
            // This case should ideally not be reached if called from purchaseItem, as selling enchantments directly is not a feature.
            // We can add a message here if needed, but for now, we just prevent the transaction.
            return false;
        }

        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(name));
        if (enchantment == null) {
            return false; // Should not happen if config is valid
        }

        // Create the enchanted book
        ItemStack enchantedBook = new ItemStack(Material.ENCHANTED_BOOK, 1);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) enchantedBook.getItemMeta();
        meta.addStoredEnchant(enchantment, amount, true);
        enchantedBook.setItemMeta(meta);

        // Give the book to the player
        HashMap<Integer, ItemStack> failedItems = player.getInventory().addItem(enchantedBook);

        if (failedItems.isEmpty()) {
            return true; // Success
        } else {
            // Inventory was full, drop the book at the player's location
            Format.sendRawMessage(player, Config.get().getNotEnoughSpace(), r);
            player.getWorld().dropItem(player.getLocation(), failedItems.get(0));
            return true; // The purchase itself was successful, even if dropped.
        }
    }

    private static boolean canEnchant(ItemStack item, Enchantment enchantment, int addedLevels) {
        if (!enchantment.canEnchantItem(item)) {
            return false;
        } else if (item.containsEnchantment(enchantment)) {
            return item.getEnchantmentLevel(enchantment) + addedLevels <= enchantment.getMaxLevel();
        } 
        for (Enchantment enchantment2 : item.getEnchantments().keySet()) {
            if (enchantment2.conflictsWith(enchantment)) {
                return false;
            }
        }
        return true;
    }

    private static double scalePriceToDurability(ItemStack item, double sellPrice) {
        if (item.getItemMeta() instanceof Damageable) {
            Damageable damageable = (Damageable) item.getItemMeta();
            double durability = damageable.getHealth();
            double maxDurability = item.getType().getMaxDurability();

            if (Config.get().isDurabilityFunction()) {
                return sellPrice * (maxDurability - durability) / maxDurability;
            }

            if (durability != maxDurability) {
                return 0;
            }

        }

        return sellPrice;
    }

}