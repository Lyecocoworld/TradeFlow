package com.github.lye.gui;

import com.github.lye.TradeFlow;
import com.github.lye.data.Shop;
import com.github.lye.gameplay.rumors.RumorManager;
import com.github.lye.util.Format;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class FlashSaleGui {

    private final TradeFlow plugin;
    private final Gui gui;

    public FlashSaleGui(TradeFlow plugin, Player player) {
        this.plugin = plugin;

        this.gui = Gui.gui()
                .rows(3)
                .title(GuiTextCache.title("Ventes Flash"))
                .disableAllInteractions()
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));
        BackgroundUtil.fillBackground(gui, plugin);

        buildContent(player);
        addNavigation(player);
    }

    private void buildContent(Player player) {
        List<RumorManager.FlashSale> sales = plugin.getRumorManager().getFlashSales();
        // Slots mirroring RumorGui: 11, 13, 15
        int[] slots = {11, 13, 15};

        for (int i = 0; i < Math.min(sales.size(), slots.length); i++) {
            RumorManager.FlashSale sale = sales.get(i);
            Shop shop = plugin.getShopUtil().getShop(sale.itemKey, true);
            if (shop == null) continue;

            Material mat = Material.matchMaterial(sale.itemKey.toUpperCase());
            if (mat == null) mat = Material.BARRIER;

            ItemStack icon = new ItemStack(mat);
            icon.editMeta(meta -> {
                meta.displayName(MiniMessage.miniMessage().deserialize("<gold><b>" + Format.prettifyName(sale.itemKey) + "</b></gold>")
                        .decoration(TextDecoration.ITALIC, false));

                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(MiniMessage.miniMessage().deserialize(
                        "<gray>Prix: <gold>" + Format.currency(sale.price) + "</gold> <gray><st>" + Format.currency(shop.getPrice()) + "</st></gray>")
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(MiniMessage.miniMessage().deserialize("<gray>Réduction: <gold>-" + sale.discountPercent + "%</gold>")
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(MiniMessage.miniMessage().deserialize("<gray>Stock: <gold>" + sale.stock + "</gold>")
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.empty());
                lore.add(MiniMessage.miniMessage().deserialize("<yellow>Clic pour acheter</yellow>")
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
            });

            gui.setItem(slots[i], new GuiItem(icon, event -> {
                buyFlashSale(player, sale);
                // Refresh GUI
                new FlashSaleGui(plugin, player).open(player);
            }));
        }
    }

    private void addNavigation(Player player) {
        // Back to Black Market Menu (Slot 18 - Bottom Left)
        ItemStack back = new ItemStack(Material.ARROW);
        back.editMeta(m -> m.displayName(
                MiniMessage.miniMessage().deserialize("<white>Retour</white>")
                        .decoration(TextDecoration.ITALIC, false)));

        gui.setItem(18, new GuiItem(back, event -> {
            new BlackMarketGui(plugin, player).open(player);
        }));
    }

    private void buyFlashSale(Player player, RumorManager.FlashSale sale) {
        if (sale.stock <= 0) {
            plugin.getMessageService().sendErrorMessage(player, "<red>Rupture de stock !</red>", null);
            return;
        }
        if (plugin.getEconomy().getBalance(player) < sale.price) {
            plugin.getMessageService().sendErrorMessage(player, "not-enough-money", null);
            return;
        }

        plugin.getEconomy().withdrawPlayer(player, sale.price);
        plugin.getInventoryService().giveItem(player, new ItemStack(Material.matchMaterial(sale.itemKey.toUpperCase()), 1));
        sale.stock--;

        player.sendMessage(MiniMessage.miniMessage().deserialize("<green>Vous avez profité de la promotion !</green>"));
    }

    public void open(Player player) {
        gui.open(player);
    }
}
