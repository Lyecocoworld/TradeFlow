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
 * Player Management GUI for admins.
 * Handles player lookup, stats, and account management.
 *
 * @author lye
 * @since 0.1
 */
public class AdminPlayersGui {

    private final TradeFlow plugin;
    private final AdminNavigator navigator;
    private final Player admin;
    private final Gui gui;

    public AdminPlayersGui(TradeFlow plugin, AdminNavigator navigator, Player admin) {
        this.plugin = plugin;
        this.navigator = navigator;
        this.admin = admin;

        this.gui = Gui.gui()
                .rows(4)
                .title(GuiTextCache.title("Administration - Joueurs"))
                .disableAllInteractions()
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));
        BackgroundUtil.fillBackground(gui, plugin);

        buildContent();
    }

    private void buildContent() {
        // --- Row 2: Player Lookup ---

        // Slot 11: Player Stats
        ItemStack playerStats = createPlayerItem(
                Material.PLAYER_HEAD,
                "Statistiques Joueur",
                "Voir les statistiques d'un joueur",
                "Clic pour rechercher"
        );
        gui.setItem(11, new GuiItem(playerStats, event -> {
            Player player = (Player) event.getWhoClicked();
            if (!AdminPermission.check(player)) return;
            player.closeInventory();
            player.sendMessage(MiniMessage.miniMessage().deserialize("<white>Usage: <yellow>/tfadmin player <pseudo></yellow></white>"));
        }));

        // Slot 13: Bank Balance
        ItemStack bankBalance = createPlayerItem(
                Material.GOLD_BLOCK,
                "Solde Bancaire",
                "Modifier le solde d'un joueur",
                "Clic pour modifier"
        );
        gui.setItem(13, new GuiItem(bankBalance, event -> {
            Player player = (Player) event.getWhoClicked();
            if (!AdminPermission.check(player)) return;
            player.closeInventory();
            player.sendMessage(MiniMessage.miniMessage().deserialize("<white>Usage: <yellow>/tfadmin setbank <pseudo> <montant></yellow></white>"));
        }));

        // Slot 15: Collection Reset
        ItemStack collectionReset = createPlayerItem(
                Material.BARRIER,
                "Réinitialiser Collection",
                "Réinitialiser la collection d'un joueur",
                "Clic pour réinitialiser"
        );
        gui.setItem(15, new GuiItem(collectionReset, event -> {
            Player player = (Player) event.getWhoClicked();
            if (!AdminPermission.check(player)) return;
            player.closeInventory();
            player.sendMessage(MiniMessage.miniMessage().deserialize("<white>Usage: <yellow>/tfadmin resetcollection <pseudo></yellow></white>"));
        }));

        // --- Row 3: Advanced Management ---

        // Slot 20: Online Players
        ItemStack onlinePlayers = createPlayerItem(
                Material.COMPASS,
                "Joueurs en Ligne",
                "Voir les joueurs connectés",
                "Clic pour voir"
        );
        gui.setItem(20, new GuiItem(onlinePlayers, event -> {
            Player player = (Player) event.getWhoClicked();
            if (!AdminPermission.check(player)) return;
            player.closeInventory();

            List<? extends Player> online = plugin.getServer().getOnlinePlayers().stream().toList();
            if (online.isEmpty()) {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<white>Aucun joueur en ligne.</white>"));
            } else {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<gold>Joueurs en ligne (" + online.size() + "):</gold>"));
                for (Player p : online) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize("<white>- <yellow>" + p.getName() + "</yellow></white>"));
                }
            }
        }));

        // Slot 22: Transaction History
        ItemStack transactions = createPlayerItem(
                Material.BOOK,
                "Historique Transactions",
                "Voir les transactions d'un joueur",
                "Clic pour rechercher"
        );
        gui.setItem(22, new GuiItem(transactions, event -> {
            Player player = (Player) event.getWhoClicked();
            if (!AdminPermission.check(player)) return;
            navigator.openTransactions(player);
        }));

        // Slot 24: Loans
        ItemStack loans = createPlayerItem(
                Material.PAPER,
                "Prêts en Cours",
                "Voir et gérer les prêts",
                "Clic pour voir"
        );
        gui.setItem(24, new GuiItem(loans, event -> {
            Player player = (Player) event.getWhoClicked();
            if (!AdminPermission.check(player)) return;
            player.closeInventory();
            player.sendMessage(MiniMessage.miniMessage().deserialize("<white>Utilisez <yellow>/tfadmin loans</yellow> pour voir les prêts.</white>"));
        }));

        // --- Navigation Bar (bottom right) ---
        NavigationBar.apply(gui, new NavigationBar.Config(4)
                .title("Joueurs")
                .onBack(() -> navigator.openMainMenu(admin))
                .showClose(true));
    }

    private ItemStack createPlayerItem(Material material, String title, String description, String actionHint) {
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
