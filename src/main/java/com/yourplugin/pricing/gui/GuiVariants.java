package com.yourplugin.pricing.gui;

import unprotesting.com.github.AutoTune;
import com.yourplugin.pricing.model.Family;
import com.yourplugin.pricing.model.ItemId;
import com.yourplugin.pricing.service.PriceService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import unprotesting.com.github.AutoTune;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Represents the sub-GUI displaying variants of a specific item family.
 * Allows players to view prices, buy/sell items, and navigate back to the main catalog.
 */
public class GuiVariants {

    private static final Logger LOGGER = Logger.getLogger(GuiVariants.class.getName());
    private final AutoTune plugin;
    private final Family family;
    private final PriceService priceService;
    private final GuiCatalog guiCatalog; // To navigate back
    private final unprotesting.com.github.config.Config config;

    public GuiVariants(AutoTune plugin, Family family, PriceService priceService, GuiCatalog guiCatalog, unprotesting.com.github.config.Config config) {
        this.plugin = plugin;
        this.family = family;
        this.priceService = priceService;
        this.guiCatalog = guiCatalog;
        this.config = config;
    }

    /**
     * Opens the variants GUI for a specific family for a player.
     * @param player The player to open the GUI for.
     */
    public void open(Player player) {
        // Create a Bukkit Inventory
        Inventory inventory = Bukkit.createInventory(null, 54, "Variants • " + formatItemId(family.getRootItem()));

        // Populate inventory with variant items
        String familyCat = com.yourplugin.pricing.gui.FamilyRegistry.familyCategory(family);
        List<ItemId> vars = new ArrayList<>(family.getVariantItems());
        vars.sort(GuiVariants.variantComparator(familyCat));

        int slot = 0;
        for (ItemId variantId : vars) {
            if (slot >= 45) break; // Limit to first page for simplicity

            ItemStack variantItemDisplay = createVariantItemDisplay(variantId, player);
            inventory.setItem(slot, variantItemDisplay);
            slot++;
        }

        // Add back button
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§r§aBack to Families");
            backButton.setItemMeta(backMeta);
        }
        inventory.setItem(48, backButton); // Example slot for back button

        player.openInventory(inventory);
        LOGGER.info("Opening GUI Variants for family: " + family.getRootItem().getFullId() + " for player: " + player.getName());
    }

    /**
     * Creates the ItemStack for a variant item in the GUI.
     * @param variantId The ItemId of the variant.
     * @return The ItemStack representing the variant.
     */
    private ItemStack createVariantItemDisplay(ItemId variantId, Player player) {
        String niceName = formatItemId(variantId);
        LockReason lock = lockState(player, variantId, priceService, plugin);

        if (lock == LockReason.NONE) {
            Material material = Material.matchMaterial(variantId.getKey());
            if (material == null) {
                material = Material.BARRIER; // Fallback
            }
            ItemStack itemStack = new ItemStack(material);
            ItemMeta meta = itemStack.getItemMeta();

            if (meta != null) {
                meta.setDisplayName("§r§f" + niceName);
                List<String> lore = new ArrayList<>();

                Optional<Double> priceOpt = priceService.getPrice(variantId);
                double price = priceOpt.get(); // Should be present and not infinite if LockReason is NONE

                lore.add(String.format("§aUnit Price: §f$%.2f", price));
                lore.add(String.format("§aStack Price: §f$%.2f", price * 64));
                lore.add("§7Left-click: +1");
                lore.add("§7Shift + Left-click: +16");
                lore.add("§7Right-click: Enter Quantity");
                meta.setLore(lore);
                itemStack.setItemMeta(meta);
            }
            return itemStack;
        } else {
            String familyCat = com.yourplugin.pricing.gui.FamilyRegistry.familyCategory(family);
            if (config.getGuiLockedStyle().equalsIgnoreCase("pane")) {
                return GuiVariants.lockedPane(niceName, variantId.getFullId(), lock, familyCat, config);
            } else { // Default to ghost style
                return GuiVariants.lockedGhost(variantId, niceName, lock, familyCat, config);
            }
        }
    }

    /**
     * Handles a click event in the variants GUI.
     * @param player The player who clicked.
     * @param clickedItem The ItemStack that was clicked.
     * @param slot The slot that was clicked.
     * @param clickType The type of click (e.g., LEFT, SHIFT_LEFT, RIGHT).
     */
    public void handleClick(Player player, ItemStack clickedItem, int slot, ClickType clickType) {
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (slot == 48 && clickedItem.getType() == Material.ARROW) {
            // Back button clicked
            guiCatalog.open(player);
            return;
        }

        // Identify the variant item clicked
        ItemId variantId = new ItemId("minecraft", clickedItem.getType().name().toLowerCase()); // Simplified

        LockReason lock = lockState(player, variantId, priceService, plugin);
        if (lock != LockReason.NONE) {
            player.sendMessage("§7[" + formatItemId(variantId) + "] §cIndisponible: " + reasonToText(lock));
            return;
        }

        Optional<Double> priceOpt = priceService.getPrice(variantId);
        // Price should be present and not infinite if LockReason is NONE
        double price = priceOpt.get();
        int quantity = 0;

        switch (clickType) {
            case LEFT:
                quantity = 1;
                break;
            case SHIFT_LEFT:
                quantity = 16; // Example quantity
                break;
            case RIGHT:
                // Open a chat prompt for quantity input
                player.sendMessage("§eEnter the quantity you wish to buy/sell:");
                // You would need to store the player's context (e.g., an awaiting input map)
                // and handle the next chat message from this player.
                break;
            default:
                return;
        }

        if (quantity > 0) {
            // Perform buy/sell logic here
            player.sendMessage(String.format("§aAttempting to buy/sell %d of %s for $%.2f", quantity, formatItemId(variantId), price * quantity));
        }

        LOGGER.info("Player " + player.getName() + " clicked in GUI Variants for family: " + family.getRootItem().getFullId() + ", item: " + variantId.getFullId() + ", quantity: " + quantity);
    }

    /**
     * Determines the reason why an item might be locked in the GUI.
     * @param p The player viewing the GUI.
     * @param id The ItemId of the item to check.
     * @param priceSvc The PriceService to query for prices.
     * @return The LockReason, or NONE if the item is available.
     */
    public static LockReason lockState(Player p, ItemId id, PriceService priceSvc, unprotesting.com.github.AutoTune plugin) {
        Optional<Double> priceOpt = priceSvc.getPrice(id);
        if (priceOpt.isEmpty() || priceOpt.get() == Double.POSITIVE_INFINITY) return LockReason.PRICE_UNKNOWN;

        // Retrieve the Shop object from the old system
        unprotesting.com.github.data.Shop shop = unprotesting.com.github.data.ShopUtil.getShop(plugin.getDatabase(), id.getKey(), true);
        if (shop != null && !shop.isUnlocked(p.getUniqueId())) {
            return LockReason.COLLECT_FIRST_LOCKED;
        }

        if (!p.hasPermission("autotune.buy." + id.getKey())) return LockReason.NO_PERMISSION; // example
        // if (playerLevel(p) < neededLevel(id)) return LockReason.LEVEL_TOO_LOW;
        // if (!stockService.hasStock(id)) return LockReason.OUT_OF_STOCK;
        return LockReason.NONE;
    }


    /**
     * Creates a gray stained glass pane representing a locked item.
     * @param displayName The display name of the item.
     * @param behindKey The key of the item behind the lock.
     * @param reason The reason for the lock.
     * @return An ItemStack representing the locked pane.
     */
    public static ItemStack lockedPane(String displayName, String behindKey, LockReason reason, String familyCat, unprotesting.com.github.config.Config config) {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName("§7🔒 " + displayName);
            List<String> lore = new ArrayList<>();
            lore.add("§8Contenu verrouillé:");
            lore.add("§7→ §f" + behindKey);
            lore.add("");
            lore.add(switch (reason) {
               case PRICE_UNKNOWN -> "§cPrix indisponible pour le moment.";
               case NO_PERMISSION -> "§cPermission requise.";
               case LEVEL_TOO_LOW -> "§cNiveau insuffisant.";
               case OUT_OF_STOCK -> "§cRupture de stock.";
               case COLLECT_FIRST_LOCKED -> "§cObjet non collecté.";
               default -> "§7Indisponible.";
            });

            if (config.isGuiLockedTipsMetalsHint() && familyCat.equals("metals")) {
                lore.add("");
                lore.add("§8Astuce: §79 nuggets = 1 ingot, 9 ingots = 1 block");
            }
            m.setLore(lore);
            it.setItemMeta(m);
        }
        return it;
    }

    /**
     * Creates a ghosted version of the item, indicating it's locked.
     * @param id The ItemId of the item.
     * @param niceName The formatted name of the item.
     * @param reason The reason for the lock.
     * @return An ItemStack representing the locked ghost item.
     */
    public static ItemStack lockedGhost(ItemId id, String niceName, LockReason reason, String familyCat, unprotesting.com.github.config.Config config) {
        Material mat = Material.matchMaterial(id.getKey().toUpperCase());
        ItemStack it = new ItemStack(mat != null ? mat : Material.PAPER);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName("§8🔒 " + niceName);
            List<String> lore = new ArrayList<>();
            lore.add("§7Indisponible: §c" + reasonToText(reason));
            lore.add("§8" + id.getKey());

            if (reason == LockReason.COLLECT_FIRST_LOCKED) {
                lore.add("");
                lore.add("§eAstuce: Collectez cet objet pour le débloquer.");
            }

            if (familyCat.equals("metals")) {
                lore.add("");
                lore.add("§8Astuce: §79 nuggets = 1 ingot, 9 ingots = 1 block");
            }
            m.setLore(lore);
            // Astuce: ajoute un enchant pour un léger "glow" + hide
            m.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true);
            m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            it.setItemMeta(m);
        }
        return it;
    }

    public static String reasonToText(LockReason r){
        return switch (r) {
            case PRICE_UNKNOWN -> "prix inconnu";
            case NO_PERMISSION -> "pas la permission";
            case LEVEL_TOO_LOW -> "niveau insuffisant";
            case OUT_OF_STOCK -> "stock épuisé";
            default -> "indisponible";
        };
    }

    public static String formatItemId(ItemId itemId) {
        String key = itemId.getKey().replace('_', ' ');
        return Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }

    // --- Variant Sorting Heuristics ---

    private static final List<String> WOOD_ORDER = List.of("planks","slab","stairs","fence","trapdoor","door");
    private static final List<String> METAL_ORDER = List.of("nugget","ingot","block");

    /**
     * Ranks an ItemId for sorting within a family based on its type and family category.
     * @param id The ItemId to rank.
     * @param familyCat The category of the family (e.g., "wood", "metals").
     * @return An integer rank, lower means higher priority.
     */
    public static int variantRank(ItemId id, String familyCat) {
        String k = id.getKey();
        List<String> ref = switch (familyCat) {
            case "wood" -> WOOD_ORDER;
            case "metals" -> METAL_ORDER;
            default -> List.of();
        };
        for (int i=0; i<ref.size(); i++) {
            if (k.endsWith("_" + ref.get(i))) return i; // Corrected line
        }
        return 999; // alpha fallback
    }

    /**
     * Comparator for sorting ItemId objects within a family.
     * @param familyCat The category of the family.
     * @return A Comparator for ItemId.
     */
    public static java.util.Comparator<ItemId> variantComparator(String familyCat) {
        return java.util.Comparator.<ItemId>comparingInt(id -> variantRank(id, familyCat))
                         .thenComparing(ItemId::getKey);
    }

    // Dummy ClickType enum for demonstration
    public enum ClickType {
        LEFT, SHIFT_LEFT, RIGHT, OTHER
    }

    public enum LockReason { NONE, PRICE_UNKNOWN, NO_PERMISSION, LEVEL_TOO_LOW, OUT_OF_STOCK, COLLECT_FIRST_LOCKED }

    // Factory for dependency injection
    public static class Factory {
        private final AutoTune plugin;
        private final PriceService priceService;
        private final GuiCatalog guiCatalog;
        private final unprotesting.com.github.config.Config config;

        public Factory(AutoTune plugin, PriceService priceService, GuiCatalog guiCatalog, unprotesting.com.github.config.Config config) {
            this.plugin = plugin;
            this.priceService = priceService;
            this.guiCatalog = guiCatalog;
            this.config = config;
        }

        public GuiVariants create(Family family) {
            return new GuiVariants(plugin, family, priceService, guiCatalog, config);
        }
    }
}