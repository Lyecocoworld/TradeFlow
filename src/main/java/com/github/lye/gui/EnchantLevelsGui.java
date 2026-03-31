package com.github.lye.gui;

import com.github.lye.TradeFlow;
import com.github.lye.gui.state.PlayerShopState;
import com.github.lye.util.Format;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class EnchantLevelsGui {

    private final TradeFlow plugin;
    private final GuiNavigator navigator;
    private final PlayerShopState state;
    private final Player viewer;
    private final Gui gui;

    public EnchantLevelsGui(TradeFlow plugin, GuiNavigator navigator, PlayerShopState state, Player viewer) {
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

        buildContent();
        buildNavigation();
    }

    private void buildContent() {
        String enchantShopName = state.getItemName();
        if (enchantShopName == null || enchantShopName.isBlank()) return;

        Enchantment ench = Enchantment.getByKey(NamespacedKey.minecraft(enchantShopName.toLowerCase()));
        if (ench == null) {
            navigator.openSection(viewer, state.getSectionName());
            return;
        }

        int maxLevel = ench.getMaxLevel();
        int[] slots = new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25
        };

        for (int level = 1; level <= maxLevel && level - 1 < slots.length; level++) {
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            int finalLevel = level;
            book.editMeta(m -> m.displayName(
                    ench.displayName(finalLevel).decoration(TextDecoration.ITALIC, false)));

            gui.setItem(slots[level - 1], new GuiItem(book, event -> {
                Player player = (Player) event.getWhoClicked();
                navigator.openPurchaseEnchant(player, enchantShopName, finalLevel);
            }));
        }
    }

    /**
     * Builds the navigation bar at bottom row.
     */
    private void buildNavigation() {
        NavigationBar.apply(gui, new NavigationBar.Config(6)
                .title(state.getSectionName())
                .onBack(() -> {
                    Player player = viewer;
                    navigator.openSection(player, state.getSectionName());
                })
                .onClose(() -> {
                    viewer.closeInventory();
                })
        );
    }

    public void open(Player player) {
        gui.open(player);
    }
}
