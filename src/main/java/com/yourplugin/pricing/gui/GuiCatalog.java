package com.yourplugin.pricing.gui;

import com.yourplugin.pricing.model.Family;
import com.yourplugin.pricing.model.ItemId;
import com.yourplugin.pricing.service.PriceService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Represents the main GUI catalog displaying root families.
 * Players can click on a root item to open the variants GUI.
 */
public class GuiCatalog {

    private static final Logger LOGGER = Logger.getLogger(GuiCatalog.class.getName());
    private final FamilyRegistry familyRegistry;
    private final PriceService priceService;
    private final GuiVariants.Factory guiVariantsFactory; // Factory to create GuiVariants instances
    private final com.github.lye.config.Config config;

    public GuiCatalog(com.github.lye.TradeFlow plugin, FamilyRegistry familyRegistry, PriceService priceService, com.github.lye.config.Config config) {
        this.familyRegistry = familyRegistry;
        this.priceService = priceService;
        this.config = config;
        this.guiVariantsFactory = new GuiVariants.Factory(plugin, priceService, this, config);
    }

    /**
     * Opens the main family catalog GUI for a player.
     * @param player The player to open the GUI for.
     */
    public void open(Player player) {
        // Create a Bukkit Inventory
        Inventory inventory = Bukkit.createInventory(null, 54, "TradeFlow Families");

        List<Family> families = familyRegistry.getAllFamilies();
        families.sort(FamilyRegistry.FAMILY_COMPARATOR);
        int pages = (int) Math.ceil((double) families.size() / 45); // Assuming 45 slots for items per page

        LOGGER.info(String.format("GUI Catalog initialized: %d pages, %d families.", pages, families.size()));

        // Populate inventory with root items
        int slot = 0;
        for (Family family : families) {
            if (slot >= 45) break; // Limit to first page for simplicity in this example

            ItemStack rootItemDisplay = createRootItemDisplay(family);
            inventory.setItem(slot, rootItemDisplay);
            slot++;
        }

        // Add navigation buttons (e.g., next/previous page, close) if multiple pages
        // For simplicity, not implemented here.

        player.openInventory(inventory);
    }

    /**
     * Creates the ItemStack for a root item in the catalog GUI.
     * @param family The family to create the display item for.
     * @return The ItemStack representing the root item.
     */
    private ItemStack createRootItemDisplay(Family family) {
        String familyCat = FamilyRegistry.familyCategory(family);
        Material paneMaterial = getCategoryPaneMaterial(familyCat);

        ItemStack itemStack = new ItemStack(paneMaterial);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§r§f" + formatItemId(family.getRootItem()));
            List<String> lore = new ArrayList<>();
            lore.add("§7Category: §f" + familyCat);
            lore.add("§7Click to view variants");
            meta.setLore(lore);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    /**
     * Handles a click event in the GUI catalog.
     * @param player The player who clicked.
     * @param clickedItem The ItemStack that was clicked.
     * @param slot The slot that was clicked.
     */
    public void handleClick(Player player, ItemStack clickedItem, int slot) {
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        // Logic to determine which root item was clicked
        // Convert clickedItem to ItemId (e.g., from display name or NBT if custom items)
        // For simplicity, we'll try to match by material name.
        ItemId clickedItemId = new ItemId("minecraft", clickedItem.getType().name().toLowerCase());

        // If it's a root item, open the variants GUI
        familyRegistry.getFamilyByRoot(clickedItemId).ifPresent(family -> {
            guiVariantsFactory.create(family).open(player);
        });
        LOGGER.info("Player " + player.getName() + " clicked in GUI Catalog. Item: " + clickedItemId.getFullId());
    }

    private String formatItemId(ItemId itemId) {
        // Simple formatting, can be improved with proper localization
        String key = itemId.getKey().replace('_', ' ');
        return Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }

    /**
     * Returns a stained glass pane material based on the family category.
     * @param category The family category string.
     * @return A Material for the stained glass pane.
     */
    private Material getCategoryPaneMaterial(String category) {
        return switch (category) {
            case "wood" -> Material.LIME_STAINED_GLASS_PANE;
            case "metals" -> Material.YELLOW_STAINED_GLASS_PANE;
            case "stone" -> Material.LIGHT_GRAY_STAINED_GLASS_PANE;
            case "wool" -> Material.WHITE_STAINED_GLASS_PANE;
            case "redstone" -> Material.RED_STAINED_GLASS_PANE;
            case "food" -> Material.ORANGE_STAINED_GLASS_PANE;
            default -> Material.BLUE_STAINED_GLASS_PANE; // Default for misc/unknown
        };
    }

    // Factory interface for GuiVariants to avoid circular dependencies if GuiVariants also needs GuiCatalog
    public interface Factory {
        GuiVariants create(Family family);
    }
}