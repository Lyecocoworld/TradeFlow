package com.github.lye.gui;

import com.github.lye.TradeFlow;
import com.github.lye.access.AccessResolver;
import com.github.lye.config.ConfigResolver;
import com.github.lye.data.Section;
import com.github.lye.data.Shop;
import com.github.lye.data.ShopUtil;
import com.github.lye.gui.state.PlayerShopState;
import com.github.lye.util.Format;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.github.lye.util.TradeFlowLogger;
import com.github.lye.service.IMessageService;

/**
 * Section GUI - Displays items in a category with pagination.
 * <p>
 * Layout (6 rows):</p>
 * <pre>
 * ┌─────────────────────────────────────────────────────────┐
 * │  Row 1: Title bar (0-8)                                  │
 * │  Row 2-5: Item grid (4 rows x 7 cols = 28 items/page)   │
 * │  Row 6: Navigation bar                                    │
 * │    Single page:  [45]←  [49]Page  [53]✗              │
 * │    Multi page:    [45]←  [46]Prev  [49]Page  [50]→[53]✗
 * └─────────────────────────────────────────────────────────┘
 * </pre>
 *
 * @author lye
 * @since 0.1
 */
public class SectionGui {

    private final TradeFlow plugin;
    private final GuiNavigator navigator;
    private final PlayerShopState state;
    private final AccessResolver accessResolver;
    private final ConfigResolver configResolver;
    private final Player viewer;
    private final PaginatedGui gui;
    private final TradeFlowLogger logger;
    private final ShopUtil shopUtil;
    private final IMessageService messageService;

    private int currentPage = 0;
    private final int[] gridSlots = {
        10, 11, 12, 13, 14, 15, 16, // Row 2
        19, 20, 21, 22, 23, 24, 25, // Row 3
        28, 29, 30, 31, 32, 33, 34, // Row 4
        37, 38, 39, 40, 41, 42, 43  // Row 5
    };

    // Navigation slot positions for 6-row GUI
    private static final int SLOT_BACK = 45; // Far left - always back button
    private static final int SLOT_PAGE_INFO = 49;
    private static final int SLOT_PREV = 48; // 3 slots right of back button
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_CLOSE = 53;

    public SectionGui(TradeFlow plugin, GuiNavigator navigator, PlayerShopState state, Player viewer,
                      TradeFlowLogger logger, ShopUtil shopUtil, IMessageService messageService) {
        this.plugin = plugin;
        this.navigator = navigator;
        this.state = state;
        this.viewer = viewer;
        this.accessResolver = plugin.getAccessResolver();
        this.configResolver = plugin.getConfigResolver();
        this.logger = logger;
        this.shopUtil = shopUtil;
        this.messageService = messageService;

        String sectionName = state.getSectionName() != null ? state.getSectionName() : "Unknown";
        String pretty = Format.prettifyName(sectionName);

        this.gui = Gui.paginated()
                .rows(6)
                .pageSize(28)
                .title(GuiTextCache.title(pretty))
                .disableAllInteractions()
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));
        BackgroundUtil.fillBackground(gui, plugin);

        setupLayout();
        buildContent();
        updateNavigation();
    }

    /**
     * Sets up the base layout structure.
     */
    private void setupLayout() {
        ItemStack bgItem = gui.getInventory().getItem(0);
        if (bgItem == null || bgItem.getType() == Material.AIR) {
            bgItem = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
            bgItem.editMeta(m -> m.displayName(Component.text(" ")));
        }

        // Fill Row 1 (0-8)
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, new GuiItem(bgItem));
        }

        // Fill side columns
        int[] sideSlots = {9, 17, 18, 26, 27, 35, 36, 44};
        for (int slot : sideSlots) {
            gui.setItem(slot, new GuiItem(bgItem));
        }

        // Clear grid slots
        for (int slot : gridSlots) {
            gui.setItem(slot, new GuiItem(new ItemStack(Material.AIR)));
        }
    }

    private void buildContent() {
        // Clear grid first
        for (int slot : gridSlots) {
            gui.setItem(slot, new GuiItem(new ItemStack(Material.AIR)));
        }

        Section currentSection = shopUtil.getSection(state.getSectionName());
        if (currentSection == null) {
            logger.warning("[GUI] SectionGui: section '" + state.getSectionName() + "' not found.");
            return;
        }

        List<Map.Entry<String, Shop>> entries = new ArrayList<>(currentSection.getShops().entrySet());

        int pageSize = gridSlots.length;
        int startIndex = currentPage * pageSize;
        int endIndex = Math.min(startIndex + pageSize, entries.size());

        // Safety check for out of bounds
        if (startIndex >= entries.size() && currentPage > 0) {
            currentPage = 0;
            startIndex = 0;
            endIndex = Math.min(pageSize, entries.size());
        }

        int slotIndex = 0;
        for (int i = startIndex; i < endIndex; i++) {
            Map.Entry<String, Shop> entry = entries.get(i);
            String key = entry.getKey();
            Shop shop = entry.getValue();
            if (shop == null) continue;

            String shopName = shop.getName() != null ? shop.getName() : key;
            gui.setItem(gridSlots[slotIndex], buildShopItem(shop, shopName));
            slotIndex++;
        }

        gui.update();
    }

    /**
     * Updates the navigation bar with standard layout.
     * Single page:  [45]←  [49]Page  [53]✗
     * Multi page:   [45]←  [46]Prev  [49]Page  [50]→[53]✗
     */
    private void updateNavigation() {
        Section currentSection = shopUtil.getSection(state.getSectionName());
        if (currentSection == null) return;

        int totalItems = currentSection.getShops().size();
        int totalPages = (int) Math.ceil((double) totalItems / gridSlots.length);
        if (totalPages == 0) totalPages = 1;

        // Determine if we have pagination (more than 1 page)
        boolean hasPagination = totalPages > 1;

        // Fill navigation row with background
        ItemStack bgItem = gui.getInventory().getItem(0);
        if (bgItem == null || bgItem.getType() == Material.AIR) {
            bgItem = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
            bgItem.editMeta(m -> m.displayName(Component.text(" ")));
        }

        for (int i = 45; i <= 53; i++) {
            gui.setItem(i, new GuiItem(bgItem));
        }

        // Back button (Slot 45) - Always at far left, never moves
        ItemStack backButton = NavigationBar.createBackButton();
        gui.setItem(SLOT_BACK, new GuiItem(backButton, event -> {
            Player player = (Player) event.getWhoClicked();
            navigator.goBack(player);
        }));

        // Page info (Slot 49) - always centered
        ItemStack pageInfo = NavigationBar.createPageInfoButton(currentPage + 1, totalPages);
        gui.setItem(SLOT_PAGE_INFO, new GuiItem(pageInfo));

        // Previous page (Slot 46) - only when has pagination
        if (hasPagination && currentPage > 0) {
            ItemStack prevButton = NavigationBar.createPreviousButton();
            gui.setItem(SLOT_PREV, new GuiItem(prevButton, event -> {
                currentPage--;
                buildContent();
                updateNavigation();
            }));
        }

        // Next page (Slot 50) - only when has pagination
        if (hasPagination && currentPage < totalPages - 1) {
            ItemStack nextButton = NavigationBar.createForwardButton();
            gui.setItem(SLOT_NEXT, new GuiItem(nextButton, event -> {
                currentPage++;
                buildContent();
                updateNavigation();
            }));
        }

        // Close button (Slot 53) - Bottom right
        ItemStack closeButton = NavigationBar.createCloseButton();
        gui.setItem(SLOT_CLOSE, new GuiItem(closeButton, event -> {
            event.getWhoClicked().closeInventory();
        }));

        gui.update();
    }

    private GuiItem buildShopItem(Shop shop, String shopId) {
        Material material = shop.isEnchantment()
                ? Material.ENCHANTED_BOOK
                : Material.matchMaterial(shopId.toUpperCase());
        if (material == null) material = Material.BARRIER;

        boolean lockedAtBuild = (accessResolver.resolve(viewer, shopId) != com.github.lye.access.Decision.UNLOCKED);
        ItemStack displayItem = buildDisplayItem(viewer, shop, shopId, lockedAtBuild);

        return new GuiItem(displayItem, event -> {
            Player player = (Player) event.getWhoClicked();
            boolean locked = (accessResolver.resolve(player, shopId) != com.github.lye.access.Decision.UNLOCKED);

            if (locked) {
                String mode = configResolver.resolveCFMode(player, shopId).name();
                messageService.sendErrorMessage(player, "not-unlocked",
                        Placeholder.parsed("collect-first-setting", mode));
                return;
            }

            logger.info("[GUI] SectionGui click: player=" + player.getName()
                    + ", item=" + shopId);

            if (shop.isEnchantment()) {
                navigator.openEnchantLevels(player, shopId);
            } else {
                navigator.openPurchase(player, shopId);
            }
        });
    }

    private ItemStack buildDisplayItem(Player player, Shop shop, String shopId, boolean locked) {
        Material material = shop.isEnchantment()
                ? Material.ENCHANTED_BOOK
                : Material.matchMaterial(shopId.toUpperCase());
        if (material == null) material = Material.BARRIER;

        ItemStack displayItem = new ItemStack(material);
        List<Component> lore = new ArrayList<>();

        if (locked) {
            String unlockMode = configResolver.resolveCFMode(player, shopId).name();
            Component unlockInfo = "PLAYER".equalsIgnoreCase(unlockMode)
                    ? Format.getComponent("lore-how-to-unlock-player")
                    : Format.getComponent("lore-how-to-unlock-server");
            lore.add(Component.text(""));
            lore.add(unlockInfo.decoration(TextDecoration.ITALIC, false));
        } else {
            try {
                double price = shop.getPrice();
                double sellPrice = shop.getSellPrice();
                int buysLeft = shopUtil.getBuysLeft(player, shopId);
                int sellsLeft = shopUtil.getSellsLeft(player, shopId);
                int maxBuys = shop.getMaxBuys();
                int maxSells = shop.getMaxSells();
                String maxBuysStr = Format.compactNumber(maxBuys);
                String maxSellsStr = Format.compactNumber(maxSells);
                String changeStr = Format.percent(shop.getChange());
                String buysLeftStr = (maxBuys < 0 ? "∞" : Format.compactNumber(buysLeft));
                String sellsLeftStr = (maxSells < 0 ? "∞" : Format.compactNumber(sellsLeft));

                // --- Visual Price Trend ---
                double basePrice = shop.getPrice();
                String trendArrow = "";
                if (price > basePrice + 0.01) trendArrow = " <green>↑</green>";
                else if (price < basePrice - 0.01) trendArrow = " <red>↓</red>";

                for (String line : plugin.getMessageSettings().getShopLore()) {
                    TagResolver resolver = TagResolver.resolver(
                            Placeholder.parsed("price", Format.currency(price) + trendArrow),
                            Placeholder.parsed("sell-price", Format.currency(sellPrice)),
                            Placeholder.parsed("buys-left", buysLeftStr),
                            Placeholder.parsed("sells-left", sellsLeftStr),
                            Placeholder.parsed("max-buys", maxBuysStr),
                            Placeholder.parsed("max-sells", maxSellsStr),
                            Placeholder.parsed("change", changeStr)
                    );
                    lore.add(MiniMessage.miniMessage()
                            .deserialize(line, resolver)
                            .decoration(TextDecoration.ITALIC, false));
                }

                // Virtual Stock Display (Central Bank)
                if (plugin.getCentralBankStockManager() != null) {
                    int currentStock = plugin.getCentralBankStockManager().getCurrentStock(shop);
                    int idealStock = plugin.getCentralBankStockManager().getIdealStock(shop);
                    String stockStatus = "<green>Abondant</green>";
                    if (currentStock < idealStock * 0.25) stockStatus = "<red><b>PÉNURIE</b></red>";
                    else if (currentStock < idealStock * 0.50) stockStatus = "<yellow>Tendu</yellow>";

                    lore.add(Component.empty());
                    lore.add(MiniMessage.miniMessage().deserialize("<gray>Disponibilité Mondiale : " + stockStatus).decoration(TextDecoration.ITALIC, false));
                    lore.add(MiniMessage.miniMessage().deserialize("<gray>Volume : <white>" + Format.compactNumber(currentStock) + "</white> unités").decoration(TextDecoration.ITALIC, false));

                    if (plugin.getCentralBankStockManager().isPublicOrderActive(shop)) {
                        lore.add(MiniMessage.miniMessage().deserialize("<gold><b>⚡ Commande Publique (+20%)</b></gold>").decoration(TextDecoration.ITALIC, false));
                    }
                }

                // Add Specific Trend (Hot/Crash) indicator
                if (plugin.getMarketTrendManager() != null) {
                    Double specific = plugin.getMarketTrendManager().getSpecificTrend(shopId);
                    if (specific != null) {
                        lore.add(Component.empty());
                        if (specific > 1.0) {
                            lore.add(MiniMessage.miniMessage().deserialize("<gold>🔥 <bold>HOT ITEM</bold> (High Demand)</gold>").decoration(TextDecoration.ITALIC, false));
                        } else if (specific < 1.0) {
                            lore.add(MiniMessage.miniMessage().deserialize("<dark_red>📉 <bold>MARKET CRASH</bold> (Panic Sell)</dark_red>").decoration(TextDecoration.ITALIC, false));
                        }
                    }
                }

            } catch (Throwable t) {
                logger.severe("Failed to build lore for " + shopId, t);
            }
        }

        displayItem.editMeta(meta -> {
            Component baseDisplayName;
            if (shop.isEnchantment()) {
                Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(shopId.toLowerCase()));
                baseDisplayName = (enchantment != null)
                        ? enchantment.displayName(1)
                        : Component.text(shopId);
            } else {
                baseDisplayName = GuiTextCache.boldDisplayName(shopId);
            }

            if (locked) {
                meta.displayName(
                        Component.text("✖ ")
                                .append(baseDisplayName)
                                .decoration(TextDecoration.ITALIC, false));
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else {
                meta.displayName(baseDisplayName.decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        });

        return displayItem;
    }

    public void open(Player player) {
        gui.open(player);
    }
}
