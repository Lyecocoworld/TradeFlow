package com.github.lye.gui;

import com.github.lye.TradeFlow;
import com.github.lye.data.Database;
import com.github.lye.gui.framework.TriumphGuiAdapter;
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
import java.util.Map;

/**
 * Shop Management GUI for admins.
 * Handles shop prices, stock, and configuration.
 *
 * @author lye
 * @since 0.1
 */
public class AdminShopsGui {

    private final TradeFlow plugin;
    private final AdminNavigator navigator;
    private final Player admin;
    private final Gui gui;

    public AdminShopsGui(TradeFlow plugin, AdminNavigator navigator, Player admin) {
        this.plugin = plugin;
        this.navigator = navigator;
        this.admin = admin;

        this.gui = Gui.gui()
                .rows(4)
                .title(GuiTextCache.title("Administration - Boutiques"))
                .disableAllInteractions()
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));
        BackgroundUtil.fillBackground(gui, plugin);

        buildContent();
    }

    private void buildContent() {
        Database db = plugin.getServices().get(Database.class);
        int shopCount = db != null && db.getShops() != null ? db.getShops().size() : 0;

        // --- Row 2: Shop Management ---

        // Slot 11: Set Price
        ItemStack setPrice = createShopItem(
                Material.GOLD_NUGGET,
                "Définir un Prix",
                "Modifier le prix d'un article",
                "Clic pour modifier"
        );
        gui.setItem(11, new GuiItem(setPrice, event -> {
            Player player = (Player) event.getWhoClicked();
            if (!AdminPermission.check(player)) return;
            player.closeInventory();
            player.sendMessage(MiniMessage.miniMessage().deserialize("<white>Usage: <yellow>/tfadmin setprice <article> <prix></yellow></white>"));
        }));

        // Slot 13: Shop List
        ItemStack shopList = createShopItem(
                Material.CHEST,
                "Liste des Boutiques",
                "Voir toutes les boutiques (" + shopCount + " au total)",
                "Clic pour voir"
        );
        gui.setItem(13, new GuiItem(shopList, event -> {
            Player player = (Player) event.getWhoClicked();
            if (!AdminPermission.check(player)) return;
            player.closeInventory();
            player.sendMessage(MiniMessage.miniMessage().deserialize("<white>Utilisez <yellow>/tfadmin shops</yellow> pour voir la liste.</white>"));
        }));

        // Slot 15: Remove Shop
        ItemStack removeShop = createShopItem(
                Material.BARRIER,
                "Supprimer une Boutique",
                "Retirer une boutique du système",
                "Clic pour supprimer"
        );
        gui.setItem(15, new GuiItem(removeShop, event -> {
            Player player = (Player) event.getWhoClicked();
            if (!AdminPermission.check(player)) return;
            player.closeInventory();
            player.sendMessage(MiniMessage.miniMessage().deserialize("<white>Usage: <yellow>/tfadmin removeshop <article></yellow></white>"));
        }));

        // --- Row 3: Configuration ---

        // Slot 20: Update Prices
        ItemStack update = createShopItem(
                Material.COMMAND_BLOCK,
                "Mise à jour Prix",
                "Mettre à jour les prix automatiquement",
                "Clic pour mettre à jour"
        );
        gui.setItem(20, new GuiItem(update, event -> {
            Player player = (Player) event.getWhoClicked();
            if (!AdminPermission.check(player)) return;
            player.closeInventory();
            player.performCommand("tfadmin update");
        }));

        // Slot 22: Global Stock
        ItemStack globalStock = createShopItem(
                Material.HOPPER,
                "Stock Global",
                "Gérer le stock global",
                "Clic pour gérer"
        );
        gui.setItem(22, new GuiItem(globalStock, event -> {
            Player player = (Player) event.getWhoClicked();
            if (!AdminPermission.check(player)) return;
            player.closeInventory();
            player.sendMessage(MiniMessage.miniMessage().deserialize("<white>Utilisez <yellow>/tfadmin bank</yellow> pour gérer la banque.</white>"));
        }));

        // Slot 24: Import/Export
        ItemStack importExport = createShopItem(
                Material.BOOKSHELF,
                "Importer/Exporter",
                "Importer ou exporter les données shops",
                "Clic pour les options"
        );
        gui.setItem(24, new GuiItem(importExport, event -> {
            Player player = (Player) event.getWhoClicked();
            if (!AdminPermission.check(player)) return;
            navigator.openSystem(player);
        }));

        // --- Navigation Bar (bottom right) ---
        NavigationBar.apply(gui, new NavigationBar.Config(4)
                .title("Boutiques")
                .onBack(() -> navigator.openMainMenu(admin))
                .showClose(true));
    }

    private ItemStack createShopItem(Material material, String title, String description, String actionHint) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(MiniMessage.miniMessage().deserialize("<gold>" + title + "</gold>")
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<white>" + description + "</white>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<yellow>" + actionHint + "</yellow>")
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(lore);
        });
        return item;
    }

    public void open(Player admin) {
        TriumphGuiAdapter.openSafe(gui, admin, plugin);
    }
}
