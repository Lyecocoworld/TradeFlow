package com.yourplugin.pricing.gui;

import com.yourplugin.pricing.model.Family;
import com.yourplugin.pricing.model.ItemId;
import com.yourplugin.pricing.service.PriceService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import com.github.lye.TradeFlow;
import com.github.lye.access.Decision;
import com.github.lye.util.TradeFlowLogger;
import com.github.lye.util.Format;

public class GuiVariants {

    private static final Logger LOGGER = Logger.getLogger(GuiVariants.class.getName());

    private final TradeFlow plugin;
    private final Family family;
    private final PriceService priceService;
    private final GuiCatalog guiCatalog;
    private final com.github.lye.config.Config config;

    public GuiVariants(TradeFlow plugin,
                       Family family,
                       PriceService priceService,
                       GuiCatalog guiCatalog,
                       com.github.lye.config.Config config) {
        this.plugin = plugin;
        this.family = family;
        this.priceService = priceService;
        this.guiCatalog = guiCatalog;
        this.config = config;
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, "Variants - " + formatItemId(family.getRootItem()));

        String familyCat = com.yourplugin.pricing.gui.FamilyRegistry.familyCategory(family);
        List<ItemId> vars = new ArrayList<>(family.getVariantItems());
        vars.sort(GuiVariants.variantComparator(familyCat));

        int slot = 0;
        for (ItemId variantId : vars) {
            if (slot >= 45) break;
            ItemStack display = createVariantItemDisplay(variantId, player);
            inventory.setItem(slot++, display);
        }

        player.openInventory(inventory);
        LOGGER.info("Opening GUI Variants for family: " + family.getRootItem().getFullId() + " for player: " + player.getName());
    }

    private ItemStack createVariantItemDisplay(ItemId variantId, Player player) {
        String niceName = formatItemId(variantId);
        LockReason lock = lockState(player, variantId, priceService, plugin);

        if (lock == LockReason.NONE) {
            Material material = Material.matchMaterial(variantId.getKey());
            if (material == null) material = Material.BARRIER;
            ItemStack it = new ItemStack(material);
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§f" + niceName);
                List<String> lore = new ArrayList<>();
                Optional<Double> priceOpt = priceService.getPrice(variantId);
                double price = priceOpt.get();
                lore.add(String.format(Locale.ROOT, "§aPrix unitaire: §f$%.2f", price));
                lore.add(String.format(Locale.ROOT, "§aPrix stack (64): §f$%.2f", price * 64));
                lore.add("§7Clic gauche: §f+1");
                lore.add("§7Shift + clic gauche: §f+16");
                lore.add("§7Clic droit: §fQuantité personnalisée");
                meta.setLore(lore);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
                it.setItemMeta(meta);
            }
            return it;
        } else {
            String familyCat = com.yourplugin.pricing.gui.FamilyRegistry.familyCategory(family);
            if (config.getGuiLockedStyle().equalsIgnoreCase("pane")) {
                return GuiVariants.lockedPane(niceName, variantId.getFullId(), lock, familyCat, config);
            } else {
                return GuiVariants.lockedGhost(variantId, niceName, lock, familyCat, config);
            }
        }
    }

    public static LockReason lockState(Player p, ItemId id, PriceService priceSvc, TradeFlow plugin) {
        TradeFlowLogger logger = Format.getLog();
        logger.info("[DEBUG] GuiVariants.lockState: Checking lock state for item: " + id.getKey());

        Optional<Double> priceOpt = priceSvc.getPrice(id);
        if (priceOpt.isEmpty() || priceOpt.get() == Double.POSITIVE_INFINITY) {
            logger.info("[DEBUG] GuiVariants.lockState: Item " + id.getKey() + " returning PRICE_UNKNOWN.");
            return LockReason.PRICE_UNKNOWN;
        }

        Decision access = plugin.getAccessResolver().resolve(p, id.getKey());
        logger.info("[DEBUG] GuiVariants.lockState: accessResolver for " + id.getKey() + " returned: " + access);
        if (access == Decision.LOCKED || access == Decision.PENDING) {
            logger.info("[DEBUG] GuiVariants.lockState: Item " + id.getKey() + " returning COLLECT_FIRST_LOCKED (" + access + ").");
            return LockReason.COLLECT_FIRST_LOCKED;
        }

        logger.info("[DEBUG] GuiVariants.lockState: Item " + id.getKey() + " returning NONE.");
        return LockReason.NONE;
    }

    public static ItemStack lockedPane(String displayName, String behindKey, LockReason reason, String familyCat, com.github.lye.config.Config config) {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName("§7[Verrouillé] §f" + displayName);
            List<String> lore = new ArrayList<>();
            lore.add("§8Contenu verrouillé:");
            lore.add("§7→ §f" + behindKey);
            lore.add("");
            lore.add(switch (reason) {
                case PRICE_UNKNOWN -> "§cPrix indisponible pour le moment.";
                case LEVEL_TOO_LOW -> "§cNiveau insuffisant.";
                case OUT_OF_STOCK -> "§cRupture de stock.";
                case COLLECT_FIRST_LOCKED -> "§cObjet non collecté.";
                default -> "§7Indisponible.";
            });
            // Hint for metals families
            if (config.isGuiLockedTipsMetalsHint() && "metals".equals(familyCat)) {
                lore.add("");
                lore.add("§8Astuce: §79 nuggets = 1 ingot, 9 ingots = 1 block");
            }
            m.setLore(lore);
            // Add subtle glow to locked items
            m.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true);
            m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            it.setItemMeta(m);
        }
        return it;
    }

    public static ItemStack lockedGhost(ItemId id, String niceName, LockReason reason, String familyCat, com.github.lye.config.Config config) {
        Material mat = Material.matchMaterial(id.getKey().toUpperCase(Locale.ROOT));
        ItemStack it = new ItemStack(mat != null ? mat : Material.PAPER);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName("§8« §f" + niceName + " §8»");
            List<String> lore = new ArrayList<>();
            lore.add("§7Indisponible: §c" + reasonToText(reason));
            lore.add("§8" + id.getKey());
            if (reason == LockReason.COLLECT_FIRST_LOCKED) {
                lore.add("");
                lore.add("§eAstuce: Collectez cet objet pour le déverrouiller.");
            }
            m.setLore(lore);
            // Add subtle glow to locked items
            m.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true);
            m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            it.setItemMeta(m);
        }
        return it;
    }

    public static String reasonToText(LockReason r) {
        return switch (r) {
            case PRICE_UNKNOWN -> "prix inconnu";
            case LEVEL_TOO_LOW -> "niveau insuffisant";
            case OUT_OF_STOCK -> "stock épuisé";
            case COLLECT_FIRST_LOCKED -> "collectez une fois pour débloquer";
            default -> "indisponible";
        };
    }

    public static String formatItemId(ItemId itemId) {
        String key = itemId.getKey().replace('_', ' ');
        return Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }

    private static final List<String> WOOD_ORDER = List.of("planks", "slab", "stairs", "fence", "trapdoor", "door");
    private static final List<String> METAL_ORDER = List.of("nugget", "ingot", "block");

    public static int variantRank(ItemId id, String familyCat) {
        String k = id.getKey();
        List<String> ref = switch (familyCat) {
            case "wood" -> WOOD_ORDER;
            case "metals" -> METAL_ORDER;
            default -> List.of();
        };
        for (int i = 0; i < ref.size(); i++) {
            if (k.endsWith("_" + ref.get(i))) return i;
        }
        return 999;
    }

    public static java.util.Comparator<ItemId> variantComparator(String familyCat) {
        return java.util.Comparator.<ItemId>comparingInt(id -> variantRank(id, familyCat))
            .thenComparing(ItemId::getKey);
    }

    public enum ClickType {
        LEFT, SHIFT_LEFT, RIGHT, OTHER
    }

    public enum LockReason { NONE, PRICE_UNKNOWN, LEVEL_TOO_LOW, OUT_OF_STOCK, COLLECT_FIRST_LOCKED }

    public static class Factory {
        private final TradeFlow plugin;
        private final PriceService priceService;
        private final GuiCatalog guiCatalog;
        private final com.github.lye.config.Config config;

        public Factory(TradeFlow plugin, PriceService priceService, GuiCatalog guiCatalog, com.github.lye.config.Config config) {
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
