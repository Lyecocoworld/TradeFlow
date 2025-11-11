package unprotesting.com.github.gui;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;
import com.github.stefvanschie.inventoryframework.pane.Pane.Priority;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import net.kyori.adventure.text.minimessage.MiniMessage;
import unprotesting.com.github.AutoTune;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.CollectFirst;
import unprotesting.com.github.data.Database;
import unprotesting.com.github.data.EconomyDataUtil;
import unprotesting.com.github.data.PurchaseUtil;
import unprotesting.com.github.data.Section;
import unprotesting.com.github.data.Shop;
import unprotesting.com.github.data.ShopUtil;
import unprotesting.com.github.util.Format;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;


public class ShopGuiManager {

    private final AutoTune plugin;
    private static OutlinePane background;

    public ShopGuiManager(@NotNull AutoTune plugin) {
        this.plugin = plugin;
    }

    public ChestGui createShopGui() {
        Config config = Config.get();
        ChestGui gui = new ChestGui(6, config.getGuiTitleShop());
        gui.setOnGlobalClick(event -> event.setCancelled(true));
        getBackground(gui);
        return gui;
    }

    public void openMainShopGui(@NotNull Player player) {
        ChestGui gui = createShopGui();
        gui.addPane(loadSectionsPane(player, gui));
        gui.show(player);
    }

    public void openShopSectionGui(@NotNull Player player, @NotNull Section section) {
        ChestGui gui = createShopGui();
        gui.addPane(loadShopPane(player, gui, section));
        gui.addPane(getGdpPane(player, gui));
        gui.show(player);
    }

    public void openPurchaseGui(@NotNull Player player, @NotNull String shopName) {
        ChestGui gui = createShopGui();

        gui.addPane(getBackToShop(player, gui, ShopUtil.getShop(shopName, true).getSection()));
        gui.addPane(getGdpPane(player, gui));
        gui.addPane(getPurchasePane(player, shopName, gui));
        gui.update(); // Update is called here, but show is not. This is for refreshing an existing GUI.
        gui.show(player); // Ensure the GUI is shown if it's a fresh open
    }

    public void openEnchantmentLevelGui(@NotNull Player player, @NotNull String shopName) {
        ChestGui gui = createShopGui();
        Shop shop = ShopUtil.getShop(shopName, true);
        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(shopName));

        if (enchantment == null) {
            Format.sendMessage(player, "Invalid enchantment shop.");
            return;
        }

        StaticPane pane = new StaticPane(0, 0, 9, 6, Priority.HIGHEST);

        for (int i = 1; i <= enchantment.getMaxLevel(); i++) {
            ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
            int level = i;
            item.editMeta(meta -> {
                meta.displayName(enchantment.displayName(level));
                ((EnchantmentStorageMeta) meta).addStoredEnchant(enchantment, level, true);
            });
            item.lore(getLoreForEnchantment(player, shopName, level, Config.get().getShopLore(), 1));


            pane.addItem(new GuiItem(item, event -> {
                event.setCancelled(true);
                openPurchaseGuiForEnchantment(player, shopName, level);
            }), i - 1, 1);
        }

        gui.addPane(pane);
        gui.addPane(getBackToShop(player, gui, shop.getSection()));
        gui.show(player);
    }

    public void openPurchaseGuiForEnchantment(@NotNull Player player, @NotNull String shopName, int level) {
        ChestGui gui = createShopGui();

        gui.addPane(getBackToShop(player, gui, ShopUtil.getShop(shopName, true).getSection()));
        gui.addPane(getGdpPane(player, gui));
        gui.addPane(getPurchasePaneForEnchantment(player, shopName, level, gui));
        gui.show(player);
    }


    private StaticPane loadSectionsPane(@NotNull Player player, @NotNull ChestGui gui) {
        Database.acquireReadLock();
        try {
            StaticPane pane = new StaticPane(0, 0, 9, 6, Priority.HIGHEST);

            for (String sectionName : ShopUtil.getSectionNames()) {
                Section section = ShopUtil.getSection(sectionName);
                GuiItem item = new GuiItem(section.getItem(), event -> {
                    event.setCancelled(true);
                    gui.getPanes().clear();
                    getBackground(gui);
                    gui.addPane(loadShopPane(player, gui, section));
                    gui.addPane(getGdpPane(player, gui));
                    gui.update();
                });
                pane.addItem(item, section.getPosX(), section.getPosY());
            }

            return pane;
        } finally {
            Database.releaseReadLock();
        }
    }

    protected PaginatedPane loadShopPane(@NotNull Player player, @NotNull ChestGui gui,
            @NotNull Section section) {
        Database.acquireReadLock();
        try {
            PaginatedPane pages = new PaginatedPane(0, 0, 9, 6, Priority.HIGHEST);
            Map<String, Shop> shops = section.getShops();
            List<String> shopNames = new ArrayList<>(shops.keySet());
            shopNames.sort(String::compareToIgnoreCase);
            int page = 0;
            List<GuiItem> itemsOnPage = new ArrayList<>();

            for (String shopName : shopNames) {
                Format.getLog().info("[DEBUG] Loading shop in GUI: " + shopName + ", isEnchantment: " + shops.get(shopName).isEnchantment());
                ItemStack item;
                Shop shop = shops.get(shopName);

                if (!Database.get().areMapsReady()) {
                    item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                    item.editMeta(meta -> meta.displayName(MiniMessage.miniMessage().deserialize("<gray>Loading Collect First...</gray>")));
                } else if (shop.isEnchantment()) {
                    item = new ItemStack(Material.ENCHANTED_BOOK);
                } else {
                    Material material = Material.matchMaterial(shopName);
                    if (material == null) {
                        material = Material.BARRIER;
                    }
                    item = new ItemStack(material);
                }

                if (!Database.get().areMapsReady()) {
                    // Lore already set for loading item
                } else if (shop.isEnchantment()) {
                    Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(shopName));
                    item.editMeta(meta -> meta.displayName(enchantment.displayName(1)));
                } else {
                    item.editMeta(meta -> meta.displayName(
                            Component.translatable((Objects.requireNonNull(
                                    Material.matchMaterial(shopName) != null ? Material.matchMaterial(shopName) : Material.BARRIER))).asComponent()));
                }

                if (Database.get().areMapsReady()) {
                    item.lore(getLore(player, shopName, Config.get().getShopLore(), 1));
                }

                itemsOnPage.add(new GuiItem(item, event -> {
                    event.setCancelled(true);
                    if (shop.isEnchantment()) {
                        openEnchantmentLevelGui(player, shopName);
                    } else {
                        openPurchaseGui(player, shopName);
                    }
                }));

                if (itemsOnPage.size() == 28 || itemsOnPage.size() + page * 28 == shops.size()) {
                    OutlinePane pane = new OutlinePane(1, 1, 7, 4, Priority.HIGHEST);

                    for (GuiItem guiItem : itemsOnPage) {
                        pane.addItem(guiItem);
                    }

                    if (page != 0) {
                        pages.addPane(page, getPageSelector(gui, pages, page - 1, 0));
                    }

                    if (itemsOnPage.size() == 28 && itemsOnPage.size() + page * 28 != shops.size()) {
                        pages.addPane(page, getPageSelector(gui, pages, page + 1, 8));
                    }

                    pages.addPane(page, pane);
                    if (section.isBackEnabled()) {
                        pages.addPane(page, getBackToSectionsPane(player, gui));
                    }
                    page++;
                    itemsOnPage.clear();
                }
            }

            return pages;
        } finally {
            Database.releaseReadLock();
        }
    }

    private StaticPane getBackToSectionsPane(@NotNull Player player, @NotNull ChestGui gui) {
        StaticPane pane = new StaticPane(0, 0, 1, 1, Priority.HIGHEST);
        ItemStack item = new ItemStack(Material.ARROW);
        item.editMeta(meta -> meta.displayName(MiniMessage.miniMessage().deserialize(Config.get().getGuiBackToMenu())));
        pane.addItem(new GuiItem(item, event -> {
            event.setCancelled(true);
            gui.getPanes().clear();
            getBackground(gui);
            gui.addPane(loadSectionsPane(player, gui));
            gui.addPane(getGdpPane(player, gui));
            gui.update();
        }), 0, 0);
        return pane;
    }

    private StaticPane getPageSelector(@NotNull ChestGui gui, @NotNull PaginatedPane pages,
            int page, int x) {
        StaticPane pane = new StaticPane(x, 5, 1, 1, Priority.HIGHEST);
        ItemStack item = new ItemStack(Material.ARROW);
        item.editMeta(meta -> meta.displayName(MiniMessage.miniMessage().deserialize(
                Config.get().getGuiGoToPage(), Placeholder.parsed("page", String.valueOf(page + 1)))));
        pane.addItem(new GuiItem(item, event -> {
            event.setCancelled(true);
            pages.setPage(page);
            gui.update();
        }), 0, 0);
        return pane;
    }

    protected StaticPane getBackToShop(@NotNull Player player, @NotNull ChestGui gui,
            @NotNull String sectionName) {
        StaticPane pane = new StaticPane(0, 0, 1, 1, Priority.HIGHEST);
        ItemStack item = new ItemStack(Material.ARROW);
        Section section = ShopUtil.getSection(sectionName);
        item.editMeta(meta -> meta.displayName(section.getItem().displayName()));
        pane.addItem(new GuiItem(item, event -> {
            event.setCancelled(true);
            gui.getPanes().clear();
            getBackground(gui);
            gui.addPane(loadShopPane(player, gui, section));
            gui.addPane(getGdpPane(player, gui));
            gui.update();
        }), 0, 0);
        return pane;
    }

    protected StaticPane getGdpPane(@NotNull Player player, @NotNull ChestGui gui) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        TagResolver r = getGdpTagResolver();
        item.editMeta(meta -> meta.displayName(MiniMessage.miniMessage().deserialize(
                Config.get().getShopGdpLore().get(0), r)));
        List<Component> lore = new ArrayList<>();

        for (int i = 1; i < Config.get().getShopGdpLore().size(); i++) {
            lore.add(MiniMessage.miniMessage().deserialize(Config.get().getShopGdpLore().get(i), r));
        }

        item.lore(lore);
        StaticPane pane = new StaticPane(8, 0, 1, 1, Priority.HIGHEST);
        pane.addItem(new GuiItem(item, event -> {
            event.setCancelled(true);
        }), 0, 0);
        return pane;
    }

    private StaticPane getPurchasePane(Player player, String shopName, ChestGui gui) {
        StaticPane pane = new StaticPane(1, 1, 7, 4);
        Shop shop = ShopUtil.getShop(shopName, true);
        final int[] amount = {1};

        // Item display
        Material material = Material.matchMaterial(shopName.toUpperCase());
        if (material == null) {
            material = Material.BARRIER;
        }
        ItemStack item = new ItemStack(material);
        item.lore(getLore(player, shopName, Config.get().getPurchaseBuyLore(), amount[0]));
        GuiItem displayItem = new GuiItem(item, event -> event.setCancelled(true));
        pane.addItem(displayItem, 3, 1);

        // Amount buttons
        pane.addItem(createAmountButton(player, shopName, displayItem, amount, 1, gui), 2, 3);
        pane.addItem(createAmountButton(player, shopName, displayItem, amount, 8, gui), 3, 3);
        pane.addItem(createAmountButton(player, shopName, displayItem, amount, 64, gui), 4, 3);

        // Buy and Sell buttons
        pane.addItem(createBuyButton(player, shop, amount), 1, 1);
        pane.addItem(createSellButton(player, shop, amount), 5, 1);

        return pane;
    }

    private StaticPane getPurchasePaneForEnchantment(Player player, String shopName, int level, ChestGui gui) {
        StaticPane pane = new StaticPane(1, 1, 7, 4);
        Shop shop = ShopUtil.getShop(shopName, true);
        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(shopName));
        final int[] amount = {1};

        // Item display
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        item.editMeta(meta -> {
            meta.displayName(enchantment.displayName(level));
            ((EnchantmentStorageMeta) meta).addStoredEnchant(enchantment, level, true);
        });
        item.lore(getLoreForEnchantment(player, shopName, level, Config.get().getPurchaseBuyLore(), amount[0]));
        GuiItem displayItem = new GuiItem(item, event -> event.setCancelled(true));
        pane.addItem(displayItem, 3, 1);

        // Amount buttons
        pane.addItem(createAmountButtonForEnchantment(player, shopName, level, displayItem, amount, 1, gui), 2, 3);
        pane.addItem(createAmountButtonForEnchantment(player, shopName, level, displayItem, amount, 8, gui), 3, 3);
        pane.addItem(createAmountButtonForEnchantment(player, shopName, level, displayItem, amount, 64, gui), 4, 3);

        // Buy button
        pane.addItem(createBuyButtonForEnchantment(player, shop, level, amount), 1, 1);

        return pane;
    }

    private GuiItem createAmountButton(Player player, String shopName, GuiItem displayItem, int[] amount, int newAmount, ChestGui gui) {
        ItemStack item = new ItemStack(Material.STONE_BUTTON, newAmount);
        item.editMeta(meta -> meta.displayName(Component.text("Amount: " + newAmount)));
        return new GuiItem(item, event -> {
            event.setCancelled(true);
            amount[0] = newAmount;
            displayItem.getItem().lore(getLore(player, shopName, Config.get().getPurchaseBuyLore(), amount[0]));
            gui.update();
        });
    }

    private GuiItem createAmountButtonForEnchantment(Player player, String shopName, int level, GuiItem displayItem, int[] amount, int newAmount, ChestGui gui) {
        ItemStack item = new ItemStack(Material.STONE_BUTTON, newAmount);
        item.editMeta(meta -> meta.displayName(Component.text("Amount: " + newAmount)));
        return new GuiItem(item, event -> {
            event.setCancelled(true);
            amount[0] = newAmount;
            displayItem.getItem().lore(getLoreForEnchantment(player, shopName, level, Config.get().getPurchaseBuyLore(), amount[0]));
            gui.update();
        });
    }

    private GuiItem createBuyButton(Player player, Shop shop, int[] amount) {
        ItemStack item = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        item.editMeta(meta -> meta.displayName(Component.text("Buy")));
        return new GuiItem(item, event -> {
            event.setCancelled(true);
            PurchaseUtil.purchaseItem(shop.getName(), player, amount[0], true);
        });
    }

    private GuiItem createBuyButtonForEnchantment(Player player, Shop shop, int level, int[] amount) {
        ItemStack item = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        item.editMeta(meta -> meta.displayName(Component.text("Buy")));
        return new GuiItem(item, event -> {
            event.setCancelled(true);
            PurchaseUtil.purchaseEnchantment(player, shop.getName(), level, amount[0]);
        });
    }

    private GuiItem createSellButton(Player player, Shop shop, int[] amount) {
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        item.editMeta(meta -> meta.displayName(Component.text("Sell")));
        return new GuiItem(item, event -> {
            event.setCancelled(true);
            PurchaseUtil.purchaseItem(shop.getName(), player, amount[0], false);
        });
    }

    protected static void getBackground(@NotNull ChestGui gui) {
        if (background != null) {
            gui.addPane(background);
            return;
        }

        GuiItem item = getBackgroundItem();
        if (item == null) {
            return;
        }

        OutlinePane pane = new OutlinePane(0, 0, 9, 6, Priority.LOWEST);
        pane.addItem(item);
        pane.setRepeat(true);
        gui.addPane(pane);
        background = pane;
    }

    protected static GuiItem getBackgroundItem() {
        Material material = Material.matchMaterial(Config.get().getBackground());
        if (material == null) {
            return null;
        }

        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.displayName(MiniMessage.miniMessage().deserialize(
                Config.get().getBackgroundPaneText())));
        return new GuiItem(item);
    }

    protected TagResolver getGdpTagResolver() {
        Database.acquireReadLock();
        try {
            double gdp = EconomyDataUtil.getGdp();
            double bal = EconomyDataUtil.getBalance();
            int capita = EconomyDataUtil.getPopulation();
            double loss = EconomyDataUtil.getLoss();
            double debt = EconomyDataUtil.getDebt();
            double inflation = EconomyDataUtil.getInflation();

            double gdpPerCapita = capita > 0 ? gdp / capita : 0.0;
            double balancePerCapita = capita > 0 ? bal / capita : 0.0;
            double lossPerCapita = capita > 0 ? loss / capita : 0.0;
            double debtPerCapita = capita > 0 ? debt / capita : 0.0;

            TagResolver.Builder builder = TagResolver.builder();
            builder.resolver(Placeholder.parsed("gdp", Format.currency(gdp)));
            builder.resolver(Placeholder.parsed("balance", Format.currency(bal)));
            builder.resolver(Placeholder.parsed("population", Format.number(capita)));
            builder.resolver(Placeholder.parsed("loss", Format.currency(loss)));
            builder.resolver(Placeholder.parsed("debt", Format.currency(debt)));
            builder.resolver(Placeholder.parsed("inflation", Format.percent(inflation)));
            builder.resolver(Placeholder.parsed("gdp-per-capita", Format.currency(gdpPerCapita)));
            builder.resolver(Placeholder.parsed("balance-per-capita", Format.currency(balancePerCapita)));
            builder.resolver(Placeholder.parsed("loss-per-capita", Format.currency(lossPerCapita)));
            builder.resolver(Placeholder.parsed("debt-per-capita", Format.currency(debtPerCapita)));

            return builder.build();
        } finally {
            Database.releaseReadLock();
        }
    }

    protected List<Component> getLore(@NotNull Player player, @NotNull String name,
            @NotNull List<String> lore, int amount) {
        Database.acquireReadLock();
        try {
            Shop shop = ShopUtil.getShop(name, true);
            AutoTune.getInstance().getLogger().info(String.format("[DEBUG] getLore() called for item %s, player %s. CollectFirstSetting: %s", name, player.getName(), shop.getSetting().getSetting()));
            boolean autosellSetting = false;

            if (Config.get().getAutosell().get(player.getUniqueId() + "." + name) != null) {
                autosellSetting = Config.get().getAutosell().getBoolean(
                        player.getUniqueId() + "." + name);
            }

            String change = Format.percent(shop.getChange());
            if (shop.getChange() > 0) {
                change = "<green>" + change + "</green>";
            } else if (shop.getChange() < 0) {
                change = "<red>" + change + "</red>";
            }

            // Create a user-friendly status for the 'Collect First' feature
            String collectFirstStatus = ""; // Default to empty string
            if (shop.getSetting().getSetting() != CollectFirst.CollectFirstSetting.NONE) {
                if (shop.isUnlocked(player.getUniqueId())) {
                    collectFirstStatus = Config.get().getLoreStatusUnlocked();
                } else {
                    collectFirstStatus = Config.get().getLoreStatusLocked();
                }
            }

            List<Component> loreComponents = new ArrayList<>();
            TagResolver resolver = TagResolver.resolver(
                    Placeholder.parsed("price", Format.currency(shop.getPrice())),
                    Placeholder.parsed("sell-price", Format.currency(shop.getSellPrice())),
                    Placeholder.parsed("total-price", Format.currency(amount * shop.getPrice())),
                    Placeholder.parsed("total-sell-price",
                            Format.currency(amount * shop.getSellPrice())),
                    Placeholder.parsed("amount", Format.number(amount)),
                    Placeholder.parsed("buys-left", Format.number(ShopUtil.getBuysLeft(player, name))),
                    Placeholder.parsed("sells-left",
                            Format.number(ShopUtil.getSellsLeft(player, name))),
                    Placeholder.parsed("max-buys", Format.number(shop.getMaxBuys())),
                    Placeholder.parsed("max-sells", Format.number(shop.getMaxSells())),
                    Placeholder.parsed("change", change),
                    Placeholder.parsed("autosell-setting", autosellSetting ? "enabled" : "disabled"));

            for (String line : lore) {
                loreComponents.add(MiniMessage.miniMessage().deserialize(line, resolver));
            }

            // Add the new, simpler lock status directly to the lore
            if (shop.getSetting().getSetting() != CollectFirst.CollectFirstSetting.NONE) {
                if (!shop.isUnlocked(player.getUniqueId())) {
                    loreComponents.add(Component.text("")); // Spacer
                    loreComponents.add(MiniMessage.miniMessage().deserialize(Config.get().getLoreStatusLocked()));
                }
            }

            return loreComponents;
        } finally {
            Database.releaseReadLock();
        }
    }

    protected List<Component> getLoreForEnchantment(@NotNull Player player, @NotNull String name, int level,
            @NotNull List<String> lore, int amount) {
        Database.acquireReadLock();
        try {
            Shop shop = ShopUtil.getShop(name, true);
            double price = shop.getPrice() * level;

            List<Component> loreComponents = new ArrayList<>();
            TagResolver resolver = TagResolver.resolver(
                    Placeholder.parsed("price", Format.currency(price)),
                    Placeholder.parsed("total-price", Format.currency(amount * price)),
                    Placeholder.parsed("amount", Format.number(amount)),
                    Placeholder.parsed("level", String.valueOf(level))
            );

            for (String line : lore) {
                loreComponents.add(MiniMessage.miniMessage().deserialize(line, resolver));
            }

            return loreComponents;
        } finally {
            Database.releaseReadLock();
        }
    }
}