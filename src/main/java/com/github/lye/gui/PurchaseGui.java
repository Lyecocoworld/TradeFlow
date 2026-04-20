package com.github.lye.gui;

import com.github.lye.TradeFlow;
import com.github.lye.config.Config;
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
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static com.github.lye.util.Format.getComponent;

/**
 * Purchase GUI - Item purchase interface with amount controls and dynamic price display.
 * <p>
 * Layout for 6-row GUI:</p>
 * <pre>
 * Row 1-2: Empty
 * Row 3:   [18][19][20]  [22-Item]  [24][25][26]
 *          [-64][-8][-1]   [Showcase] [+1][+8][+64]
 * Row 4:   [29-Buy] [30] [31-Price] [32] [33-Sell]
 * Row 5:   Empty
 * Row 6:   [45-Back]    [53-Close]
 * </pre>
 *
 * @author lye
 * @since 0.1
 */
public class PurchaseGui {

    private final TradeFlow plugin;
    private final GuiNavigator navigator;
    private final PlayerShopState state;
    private final Player viewer;
    private final Gui gui;

    // Slot positions
    private static final int SHOWCASE_SLOT = 22;
    private static final int PRICE_DISPLAY_SLOT = 31; // Center button showing total price
    private static final int BUY_BUTTON_SLOT = 29;     // Left of price button
    private static final int SELL_BUTTON_SLOT = 33;    // Right of price button

    public PurchaseGui(TradeFlow plugin, GuiNavigator navigator, PlayerShopState state, Player viewer) {
        this.plugin = plugin;
        this.navigator = navigator;
        this.state = state;
        this.viewer = viewer;

        String itemName = state.getItemName() != null ? state.getItemName() : "Unknown";
        String pretty = Format.prettifyName(itemName);

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
        buildPriceDisplayButton(); // NEW: Center button showing total price
        buildBuySellButtons();
        buildNavigationBar(); // NEW: Standard navigation bar at bottom
    }

    private void buildShowcase() {
        String shopName = state.getItemName();
        if (shopName == null || shopName.isBlank()) {
            return;
        }
        int amount = state.getAmount();

        Material mat = Material.matchMaterial(shopName.toUpperCase());
        ItemStack showcase = new ItemStack(mat == null ? Material.BARRIER : mat);
        showcase.editMeta(meta -> {
            meta.displayName(GuiTextCache.boldDisplayName(shopName));
            meta.lore(List.of(
                    getComponent("purchase-showcase-quantity",
                                    Placeholder.parsed("amount", String.valueOf(amount)))
                            .decoration(TextDecoration.ITALIC, false)
            ));
        });

        gui.setItem(SHOWCASE_SLOT, new GuiItem(showcase, event -> {}));
    }

    private void buildAmountButtons() {
        // Use MiniMessage with configurable messages instead of NamedTextColor
        ItemStack m64 = new ItemStack(Material.RED_STAINED_GLASS_PANE, 64);
        m64.editMeta(m -> m.displayName(
                getComponent("purchase-amount-decrease-64")
                        .decoration(TextDecoration.ITALIC, false)));

        ItemStack m8 = new ItemStack(Material.RED_STAINED_GLASS_PANE, 8);
        m8.editMeta(m -> m.displayName(
                getComponent("purchase-amount-decrease-8")
                        .decoration(TextDecoration.ITALIC, false)));

        ItemStack m1 = new ItemStack(Material.RED_STAINED_GLASS_PANE, 1);
        m1.editMeta(m -> m.displayName(
                getComponent("purchase-amount-decrease-1")
                        .decoration(TextDecoration.ITALIC, false)));

        ItemStack p1 = new ItemStack(Material.GREEN_STAINED_GLASS_PANE, 1);
        p1.editMeta(m -> m.displayName(
                getComponent("purchase-amount-increase-1")
                        .decoration(TextDecoration.ITALIC, false)));

        ItemStack p8 = new ItemStack(Material.GREEN_STAINED_GLASS_PANE, 8);
        p8.editMeta(m -> m.displayName(
                getComponent("purchase-amount-increase-8")
                        .decoration(TextDecoration.ITALIC, false)));

        ItemStack p64 = new ItemStack(Material.GREEN_STAINED_GLASS_PANE, 64);
        p64.editMeta(m -> m.displayName(
                getComponent("purchase-amount-increase-64")
                        .decoration(TextDecoration.ITALIC, false)));

        gui.setItem(18, new GuiItem(m64, event -> updateAmount(-64)));
        gui.setItem(19, new GuiItem(m8, event -> updateAmount(-8)));
        gui.setItem(20, new GuiItem(m1, event -> updateAmount(-1)));
        gui.setItem(24, new GuiItem(p1, event -> updateAmount(+1)));
        gui.setItem(25, new GuiItem(p8, event -> updateAmount(+8)));
        gui.setItem(26, new GuiItem(p64, event -> updateAmount(+64)));
    }

    /**
     * NEW: Central button displaying the total price dynamically.
     * This button shows the current amount, unit price, and total price.
     * Updates whenever the amount changes.
     */
    private void buildPriceDisplayButton() {
        String shopName = state.getItemName();
        if (shopName == null || shopName.isBlank()) {
            return;
        }

        Shop shop = plugin.getServices().get(ShopUtil.class).getShop(shopName, true);
        double unitPrice = (shop != null) ? shop.getPrice() : 0.0;
        int amount = state.getAmount();
        double totalPrice = unitPrice * amount;

        ItemStack priceDisplay = new ItemStack(Material.GOLD_INGOT);
        priceDisplay.editMeta(meta -> {
            // Title: Total price in gold/bold
            meta.displayName(getComponent("purchase-price-title",
                            Placeholder.parsed("total-price", Format.currency(totalPrice)))
                    .decoration(TextDecoration.ITALIC, false));

            // Subtitle: Breakdown (price x amount = total)
            meta.lore(List.of(
                    getComponent("purchase-price-subtitle",
                                    Placeholder.parsed("price", Format.currency(unitPrice)),
                                    Placeholder.parsed("amount", String.valueOf(amount)),
                                    Placeholder.parsed("total-price", Format.currency(totalPrice)))
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty()
            ));
        });

        gui.setItem(PRICE_DISPLAY_SLOT, new GuiItem(priceDisplay, event -> {
            // Clicking does nothing, just displays info
        }));
    }

    private void updateAmount(int delta) {
        int current = state.getAmount();
        int newAmount = Math.min(2304, Math.max(1, current + delta));
        state.setAmount(newAmount);

        // Rebuild all amount-dependent components
        buildShowcase();
        buildAmountButtons();
        buildPriceDisplayButton(); // Also update price display
        buildBuySellButtons();
        TriumphGuiAdapter.updateSafe(gui, viewer, plugin);
    }

    private void buildBuySellButtons() {
        String shopName = state.getItemName();
        if (shopName == null || shopName.isBlank()) {
            return;
        }
        int amount = state.getAmount();

        // BUY BUTTON (lore based on viewer)
        ItemStack buyBtn = new ItemStack(Material.EMERALD);
        List<Component> buyLore = new ArrayList<>();

        Shop shop = plugin.getServices().get(ShopUtil.class).getShop(shopName, true);
        double unitPrice = (shop != null) ? shop.getPrice() : 0.0;

        int buysLeft = 0;
        String buysLeftStr = "0";
        if (shop != null) {
            int maxBuys = shop.getMaxBuys();
            buysLeft = plugin.getServices().get(ShopUtil.class).getBuysLeft(viewer, shopName);
            buysLeftStr = (maxBuys < 0 ? "∞" : Format.compactNumber(buysLeft));
        }

        for (String line : plugin.getServices().get(IMessageSettings.class).getPurchaseBuyLore()) {
            TagResolver resolver = TagResolver.resolver(
                    Placeholder.parsed("price", Format.currency(unitPrice)),
                    Placeholder.parsed("total-price", Format.currency(unitPrice * amount)),
                    Placeholder.parsed("amount", Integer.toString(amount)),
                    Placeholder.parsed("buys-left", buysLeftStr)
            );
            buyLore.add(MiniMessage.miniMessage()
                    .deserialize(line, resolver)
                    .decoration(TextDecoration.ITALIC, false));
        }
        buyBtn.editMeta(m -> {
            m.displayName(Format.getComponent("gui-buy")
                    .decoration(TextDecoration.ITALIC, false));
            m.lore(buyLore);
        });

        gui.setItem(BUY_BUTTON_SLOT, new GuiItem(buyBtn, event -> {
            Player player = (Player) event.getWhoClicked();
            if (!TransactionLock.tryAcquire(player.getUniqueId())) {
                return; // Already processing a purchase — debounce
            }
            try {
                int amt = state.getAmount();

                Shop s = plugin.getServices().get(ShopUtil.class).getShop(shopName, true);
                double price = (s != null) ? s.getPrice() : 0.0;

                if (plugin.getServices().get(IPluginSettings.class).isEnableSellLimits()) {
                    int left = plugin.getServices().get(Database.class).getPurchasesLeft(shopName, player.getUniqueId(), true);
                    if (left - amt < 0) {
                        TagResolver rr = plugin.getServices().get(TradeExecutionService.class).getTagResolver(
                                Component.text(shopName), price, amt,
                                EconomyUtil.getEconomy().getBalance(player));
                        plugin.getServices().get(IMessageService.class).sendErrorMessage(player, plugin.getServices().get(IMessageSettings.class).getRunOutOfBuys(), rr);
                        return;
                    }
                }
                plugin.getServices().get(TradeExecutionService.class).executePurchase(shopName, player, amt, true);
            } finally {
                TransactionLock.release(player.getUniqueId());
            }
        }));

        // SELL FROM CHEST BUTTON
        ItemStack sellBtn = new ItemStack(Material.HOPPER);
        sellBtn.editMeta(m -> m.displayName(
                Format.getComponent("gui-sell-from-chest")
                        .decoration(TextDecoration.ITALIC, false)));

        gui.setItem(SELL_BUTTON_SLOT, new GuiItem(sellBtn, event -> {
            Player player = (Player) event.getWhoClicked();
            int amt = state.getAmount();
            ChestSellSelector.beginSelection(player, shopName, false, 0, amt);
            player.closeInventory();
        }));
    }

    /**
     * NEW: Standard navigation bar at bottom row
     * Uses NavigationBar utility for consistent styling
     */
    private void buildNavigationBar() {
        NavigationBar.apply(gui, new NavigationBar.Config(6)
                .onBack(() -> {
                    // Navigate back to section
                    Player player = viewer;
                    navigator.openSection(player, state.getSectionName());
                })
                .onClose(() -> {
                    // Just close the inventory
                    viewer.closeInventory();
                })
        );
    }

    public void open(Player player) {
        TriumphGuiAdapter.openSafe(gui, player, plugin);
    }
}
