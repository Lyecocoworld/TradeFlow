package com.github.lye.gui;

import com.github.lye.TradeFlow;
import com.github.lye.data.Database;
import com.github.lye.data.Shop;
import com.github.lye.data.ShopUtil;
import com.github.lye.service.IMessageService;
import com.github.lye.config.settings.IMessageSettings;
import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.service.TradeExecutionService;
import com.github.lye.events.ChestSellSelector;
import com.github.lye.gui.state.PlayerShopState;
import com.github.lye.util.EconomyUtil;
import com.github.lye.util.Format;
import com.github.lye.gui.framework.TriumphGuiAdapter;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class PurchaseEnchantGui {

    private final TradeFlow plugin;
    private final GuiNavigator navigator;
    private final PlayerShopState state;
    private final Player viewer;
    private final Gui gui;

    public PurchaseEnchantGui(TradeFlow plugin, GuiNavigator navigator, PlayerShopState state, Player viewer) {
        this.plugin = plugin;
        this.navigator = navigator;
        this.state = state;
        this.viewer = viewer;

        String enchantShopName = state.getItemName() != null ? state.getItemName() : "Unknown";
        String pretty = Format.prettifyName(enchantShopName);

        this.gui = Gui.gui()
                .rows(6)
                .title(GuiTextCache.title(pretty))
                .disableAllInteractions()
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));
        BackgroundUtil.fillBackground(gui, plugin);

        buildAll();
    }

    private void buildAll() {
        buildShowcase();
        buildAmountButtons();
        buildBuySellButtons();
        buildNavigationBar();
    }

    private void buildShowcase() {
        String enchantShopName = state.getItemName();
        if (enchantShopName == null || enchantShopName.isBlank()) return;

        int level = state.getEnchantLevel();
        int amount = state.getAmount();

        Enchantment ench = Enchantment.getByKey(NamespacedKey.minecraft(enchantShopName.toLowerCase()));
        if (ench == null) return;

        ItemStack display = new ItemStack(Material.ENCHANTED_BOOK);
        display.editMeta(m -> {
            m.displayName(ench.displayName(level).decoration(TextDecoration.ITALIC, false));
            m.lore(List.of(
                    Format.getComponent("gui-level",
                                    Placeholder.parsed("level", String.valueOf(level)))
                            .decoration(TextDecoration.ITALIC, false),
                    Format.getComponent("gui-quantity",
                                    Placeholder.parsed("amount", String.valueOf(amount)))
                            .decoration(TextDecoration.ITALIC, false)
            ));
        });

        gui.setItem(22, new GuiItem(display, event -> {}));
    }

    private void buildAmountButtons() {
        int amount = state.getAmount();

        ItemStack m64 = new ItemStack(Material.RED_STAINED_GLASS_PANE, 64);
        m64.editMeta(m -> m.displayName(Component.text("-64")
                .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)));
        ItemStack m8 = new ItemStack(Material.RED_STAINED_GLASS_PANE, 8);
        m8.editMeta(m -> m.displayName(Component.text("-8")
                .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)));
        ItemStack m1 = new ItemStack(Material.RED_STAINED_GLASS_PANE, 1);
        m1.editMeta(m -> m.displayName(Component.text("-1")
                .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)));

        ItemStack p1 = new ItemStack(Material.GREEN_STAINED_GLASS_PANE, 1);
        p1.editMeta(m -> m.displayName(Component.text("+1")
                .color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)));
        ItemStack p8 = new ItemStack(Material.GREEN_STAINED_GLASS_PANE, 8);
        p8.editMeta(m -> m.displayName(Component.text("+8")
                .color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)));
        ItemStack p64 = new ItemStack(Material.GREEN_STAINED_GLASS_PANE, 64);
        p64.editMeta(m -> m.displayName(Component.text("+64")
                .color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)));

        gui.setItem(18, new GuiItem(m64, event -> updateAmount(-64)));
        gui.setItem(19, new GuiItem(m8, event -> updateAmount(-8)));
        gui.setItem(20, new GuiItem(m1, event -> updateAmount(-1)));
        gui.setItem(24, new GuiItem(p1, event -> updateAmount(+1)));
        gui.setItem(25, new GuiItem(p8, event -> updateAmount(+8)));
        gui.setItem(26, new GuiItem(p64, event -> updateAmount(+64)));
    }

    private void updateAmount(int delta) {
        int current = state.getAmount();
        int newAmount = Math.min(2304, Math.max(1, current + delta));
        state.setAmount(newAmount);

        buildShowcase();
        buildAmountButtons();
        buildBuySellButtons();
        TriumphGuiAdapter.updateSafe(gui, viewer, plugin);
    }

    private void buildBuySellButtons() {
        String enchantShopName = state.getItemName();
        if (enchantShopName == null || enchantShopName.isBlank()) return;

        int level = state.getEnchantLevel();
        int amount = state.getAmount();

        Shop shop = plugin.getServices().get(ShopUtil.class).getShop(enchantShopName, true);
        double unitPrice = (shop != null) ? shop.getPrice() * level : 0.0;

        int buysLeft = 0;
        String buysLeftStr = "0";
        if (shop != null) {
            int maxBuys = shop.getMaxBuys();
            buysLeft = plugin.getServices().get(ShopUtil.class).getBuysLeft(viewer, enchantShopName);
            buysLeftStr = (maxBuys < 0 ? "∞" : Integer.toString(buysLeft));
        }

        ItemStack buyBtn = new ItemStack(Material.EMERALD);
        List<Component> buyLore = new ArrayList<>();

        for (String line : plugin.getServices().get(IMessageSettings.class).getPurchaseEnchantLore()) {
            TagResolver resolver = TagResolver.resolver(
                    Placeholder.parsed("price", Format.currency(unitPrice)),
                    Placeholder.parsed("total-price", Format.currency(unitPrice * amount)),
                    Placeholder.parsed("amount", String.valueOf(amount)),
                    Placeholder.parsed("level", String.valueOf(level)),
                    Placeholder.parsed("buys-left", buysLeftStr)
            );
            buyLore.add(MiniMessage.miniMessage()
                    .deserialize(line, resolver)
                    .decoration(TextDecoration.ITALIC, false));
        }

        buyBtn.editMeta(m -> {
            m.displayName(Format.getComponent("gui-buy").decoration(TextDecoration.ITALIC, false));
            m.lore(buyLore);
        });

        gui.setItem(29, new GuiItem(buyBtn, event -> {
            Player player = (Player) event.getWhoClicked();
            if (!TransactionLock.tryAcquire(player.getUniqueId())) {
                return; // Already processing a purchase — debounce
            }
            try {
                int amt = state.getAmount();

                if (plugin.getServices().get(IPluginSettings.class).isEnableSellLimits()) {
                    int left = plugin.getServices().get(Database.class).getPurchasesLeft(enchantShopName, player.getUniqueId(), true);
                    if (left - amt < 0) {
                        TagResolver rr = plugin.getServices().get(TradeExecutionService.class).getTagResolver(
                                  Shop.getDisplayName(enchantShopName, true),
                                  unitPrice,
                                  amt,
                                  EconomyUtil.getEconomy().getBalance(player));
                        plugin.getServices().get(IMessageService.class).sendErrorMessage(player, plugin.getServices().get(IMessageSettings.class).getRunOutOfBuys(), rr);
                        return;
                    }
                }
                plugin.getServices().get(TradeExecutionService.class).executeEnchantmentPurchase(enchantShopName, player, level, amt);
            } finally {
                TransactionLock.release(player.getUniqueId());
            }
        }));

        // SELL PANEL
        ItemStack sellBtn = new ItemStack(Material.HOPPER);
        sellBtn.editMeta(m -> m.displayName(
                Format.getComponent("gui-sell-panel-button").decoration(TextDecoration.ITALIC, false)));

        gui.setItem(33, new GuiItem(sellBtn, event -> {
            Player player = (Player) event.getWhoClicked();
            int amt = state.getAmount();
            ChestSellSelector.beginSelection(player, enchantShopName, true, level, amt);
            player.closeInventory();
        }));
    }

    /**
     * Builds the navigation bar at bottom row.
     */
    private void buildNavigationBar() {
        NavigationBar.apply(gui, new NavigationBar.Config(6)
                .onBack(() -> {
                    Player player = viewer;
                    navigator.openEnchantLevels(player, state.getItemName());
                })
                .onClose(() -> {
                    viewer.closeInventory();
                })
        );
    }

    public void open(Player player) {
        TriumphGuiAdapter.openSafe(gui, player, plugin);
    }
}
