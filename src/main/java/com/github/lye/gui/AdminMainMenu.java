package com.github.lye.gui;

import com.github.lye.TradeFlow;
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

/**
 * Main Admin Menu - The central hub for all administration features.
 * <p>
 * This GUI provides access to all admin tools in an organized manner.
 * It uses a clean navigation system through AdminNavigator.</p>
 *
 * <p>Layout (4 rows):</p>
 * <ul>
 *   <li>Row 1: Header with admin info</li>
 *   <li>Row 2: Core admin tools (Transactions, Notifications, Players)</li>
 *   <li>Row 3: Management tools (Economy, Shops, System)</li>
 *   <li>Row 4: Back button</li>
 * </ul>
 *
 * <p>Color scheme:</p>
 * <ul>
 *   <li>Gold (<gold>) - Titles</li>
 *   <li>White (<white> or <gray>) - Body text</li>
 *   <li>Yellow (<yellow>) - Important text / Action hints</li>
 * </ul>
 *
 * @author  lye
 * @since   0.1
 */
public class AdminMainMenu {

    private final TradeFlow plugin;
    private final AdminNavigator navigator;
    private final Gui gui;

    public AdminMainMenu(TradeFlow plugin, AdminNavigator navigator, Player admin) {
        this.plugin = plugin;
        this.navigator = navigator;

        this.gui = Gui.gui()
                .rows(4)
                .title(GuiTextCache.title("Administration TradeFlow"))
                .disableAllInteractions()
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));
        BackgroundUtil.fillBackground(gui, plugin);

        buildContent(admin);
    }

    private void buildContent(Player admin) {
        // --- Row 1: Admin Info ---
        ItemStack adminInfo = new ItemStack(Material.PLAYER_HEAD);
        adminInfo.editMeta(meta -> {
            meta.displayName(MiniMessage.miniMessage().deserialize("<gold>Administrateur: " + admin.getName() + "</gold>")
                    .decoration(TextDecoration.ITALIC, false));

            double bankBalance = plugin.getCentralBankStockManager() != null
                    ? plugin.getCentralBankStockManager().getMonetaryReserve()
                    : 0;

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<white>Banque Centrale: <yellow>" + Format.currency(bankBalance) + "</yellow></white>")
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        gui.setItem(4, new GuiItem(adminInfo));

        // --- Row 2: Core Monitoring Tools ---

        // Slot 10: Transactions
        ItemStack transactions = createMainMenuItem(
                Material.BOOKSHELF,
                "Transactions",
                "Journal des transactions serveur",
                "Voir, filtrer, rechercher"
        );
        gui.setItem(10, new GuiItem(transactions, event -> {
            Player player = (Player) event.getWhoClicked();
            navigator.openTransactions(player);
        }));

        // Slot 12: Notifications
        ItemStack notifications = createMainMenuItem(
                Material.BELL,
                "Notifications",
                "Alertes système et transactions suspectes",
                "Voir les alertes"
        );
        gui.setItem(12, new GuiItem(notifications, event -> {
            Player player = (Player) event.getWhoClicked();
            navigator.openNotifications(player);
        }));

        // Slot 14: Stats
        ItemStack stats = createMainMenuItem(
                Material.GOLD_BLOCK,
                "Statistiques",
                "Vue d'ensemble de l'économie",
                "Voir les stats"
        );
        gui.setItem(14, new GuiItem(stats, event -> {
            Player player = (Player) event.getWhoClicked();
            new AdminServerStatsGui(plugin, navigator, player).open(player);
        }));

        // Slot 16: Players
        ItemStack players = createMainMenuItem(
                Material.PLAYER_HEAD,
                "Joueurs",
                "Gestion des comptes joueurs",
                "Rechercher, modifier"
        );
        gui.setItem(16, new GuiItem(players, event -> {
            Player player = (Player) event.getWhoClicked();
            navigator.openPlayers(player);
        }));

        // --- Row 3: Management Categories ---

        // Slot 19: Economy
        ItemStack economy = createMainMenuItem(
                Material.GOLD_INGOT,
                "Économie",
                "Gestion économique globale",
                "Taxes, événements, banque"
        );
        gui.setItem(19, new GuiItem(economy, event -> {
            Player player = (Player) event.getWhoClicked();
            navigator.openEconomy(player);
        }));

        // Slot 21: Shops
        ItemStack shops = createMainMenuItem(
                Material.CHEST,
                "Boutiques",
                "Gestion des boutiques",
                "Prix, stock, config"
        );
        gui.setItem(21, new GuiItem(shops, event -> {
            Player player = (Player) event.getWhoClicked();
            navigator.openShops(player);
        }));

        // Slot 23: System
        ItemStack system = createMainMenuItem(
                Material.REDSTONE_TORCH,
                "Système",
                "Administration système",
                "Reload, backup, données"
        );
        gui.setItem(23, new GuiItem(system, event -> {
            Player player = (Player) event.getWhoClicked();
            navigator.openSystem(player);
        }));

        // Slot 25: Organizations (placeholder)
        ItemStack organizations = createMainMenuItem(
                Material.WHITE_BANNER,
                "Organisations",
                "Gestion des guildes et organisations",
                "Bientôt disponible"
        );
        gui.setItem(25, new GuiItem(organizations, event -> {
            Player player = (Player) event.getWhoClicked();
            player.sendMessage(MiniMessage.miniMessage().deserialize("<white>Les organisations seront bientôt disponibles.</white>"));
        }));

        // --- Navigation Bar (bottom right) ---
        NavigationBar.apply(gui, new NavigationBar.Config(4)
                .title("Admin Main")
                .onClose(null)); // null = default close behavior
    }

    /**
     * Creates a standardized menu item with consistent styling.
     * Color scheme: Gold title, white body, yellow action hint.
     */
    private ItemStack createMainMenuItem(Material material, String title, String description, String actionHint) {
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
        gui.open(admin);
    }
}
