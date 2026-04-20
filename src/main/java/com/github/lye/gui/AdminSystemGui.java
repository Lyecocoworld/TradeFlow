package com.github.lye.gui;

import com.github.lye.TradeFlow;
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

/**
 * System Management GUI for admins.
 * Handles reload, recalculation, and other system operations.
 *
 * @author lye
 * @since 0.1
 */
public class AdminSystemGui {

    private final TradeFlow plugin;
    private final AdminNavigator navigator;
    private final Player admin;
    private final Gui gui;

    public AdminSystemGui(TradeFlow plugin, AdminNavigator navigator, Player admin) {
        this.plugin = plugin;
        this.navigator = navigator;
        this.admin = admin;

        this.gui = Gui.gui()
                .rows(4)
                .title(GuiTextCache.title("Administration - Système"))
                .disableAllInteractions()
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));
        BackgroundUtil.fillBackground(gui, plugin);

        buildContent();
    }

    private void buildContent() {
        // --- Row 2: System Operations ---

        // Slot 11: Reload Configuration
        ItemStack reload = createSystemItem(
                Material.REDSTONE_TORCH,
                "Recharger Configuration",
                "Recharger tous les fichiers de config",
                "Clic pour recharger"
        );
        gui.setItem(11, new GuiItem(reload, event -> {
            Player player = (Player) event.getWhoClicked();
            if (!AdminPermission.check(player)) return;
            player.closeInventory();
            player.performCommand("tfadmin reload");
        }));

        // Slot 13: Recalculate Prices
        ItemStack recalculate = createSystemItem(
                Material.COMPASS,
                "Recalculer les Prix",
                "Recalculer tous les prix basés sur l'offre/demande",
                "Clic pour recalculer"
        );
        gui.setItem(13, new GuiItem(recalculate, event -> {
            Player player = (Player) event.getWhoClicked();
            if (!AdminPermission.check(player)) return;
            player.performCommand("tfadmin pricing recalculate");
            player.sendMessage(MiniMessage.miniMessage().deserialize("<gold>[TradeFlow] <white>Recalcul des prix lancé...</white>"));
            navigator.openMainMenu(player);
        }));

        // Slot 15: Export Data
        ItemStack export = createSystemItem(
                Material.WRITABLE_BOOK,
                "Exporter les Données",
                "Exporter toutes les données économiques",
                "Clic pour exporter"
        );
        gui.setItem(15, new GuiItem(export, event -> {
            Player player = (Player) event.getWhoClicked();
            if (!AdminPermission.check(player)) return;
            player.closeInventory();
            player.performCommand("tfadmin export");
        }));

        // --- Row 3: Data Management ---

        // Slot 20: Import Data
        ItemStack importItem = createSystemItem(
                Material.BOOKSHELF,
                "Importer les Données",
                "Importer des données depuis un fichier",
                "Clic pour importer"
        );
        gui.setItem(20, new GuiItem(importItem, event -> {
            Player player = (Player) event.getWhoClicked();
            if (!AdminPermission.check(player)) return;
            player.closeInventory();
            player.performCommand("tfadmin import");
        }));

        // Slot 22: Backup
        ItemStack backup = createSystemItem(
                Material.ENDER_CHEST,
                "Sauvegarde",
                "Créer une sauvegarde des données",
                "Clic pour sauvegarder"
        );
        gui.setItem(22, new GuiItem(backup, event -> {
            Player player = (Player) event.getWhoClicked();
            if (!AdminPermission.check(player)) return;
            player.performCommand("tfadmin backup");
            player.sendMessage(MiniMessage.miniMessage().deserialize("<gold>[TradeFlow] <white>Sauvegarde créée.</white>"));
        }));

        // Slot 24: Reset Collection
        ItemStack reset = createSystemItem(
                Material.BARRIER,
                "Réinitialiser Collection",
                "Réinitialiser les collections joueurs",
                "Clic pour réinitialiser"
        );
        gui.setItem(24, new GuiItem(reset, event -> {
            Player player = (Player) event.getWhoClicked();
            if (!AdminPermission.check(player)) return;
            player.closeInventory();
            player.performCommand("tfadmin resetcollection");
        }));

        // --- Navigation Bar (bottom right) ---
        NavigationBar.apply(gui, new NavigationBar.Config(4)
                .title("Système")
                .onBack(() -> navigator.openMainMenu(admin))
                .showClose(true));
    }

    private ItemStack createSystemItem(Material material, String title, String description, String actionHint) {
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
