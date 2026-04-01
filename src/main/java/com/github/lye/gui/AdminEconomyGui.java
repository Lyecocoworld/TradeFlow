package com.github.lye.gui;

import com.github.lye.TradeFlow;
import com.github.lye.events.EconomicEventManager;
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
 * Economy Management GUI for admins.
 * Handles stats, taxes, events, and central bank.
 *
 * @author lye
 * @since 0.1
 */
public class AdminEconomyGui {

    private final TradeFlow plugin;
    private final AdminNavigator navigator;
    private final Player admin;
    private final Gui gui;

    public AdminEconomyGui(TradeFlow plugin, AdminNavigator navigator, Player admin) {
        this.plugin = plugin;
        this.navigator = navigator;
        this.admin = admin;

        this.gui = Gui.gui()
                .rows(4)
                .title(GuiTextCache.title("Administration - Économie"))
                .disableAllInteractions()
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));
        BackgroundUtil.fillBackground(gui, plugin);

        buildContent();
    }

    private void buildContent() {
        double bankBalance = plugin.getCentralBankStockManager() != null
                ? plugin.getCentralBankStockManager().getMonetaryReserve()
                : 0;
        double totalTaxes = plugin.getTaxManager() != null
                ? plugin.getTaxManager().getTotalTaxesCollected()
                : 0;

        // --- Row 2: Economic Overview ---

        // Slot 11: Server Stats
        ItemStack stats = createEconomyItem(
                Material.GOLD_BLOCK,
                "Statistiques Serveur",
                "Voir les statistiques économiques",
                "Banque: " + Format.currency(bankBalance),
                "Clic pour voir"
        );
        gui.setItem(11, new GuiItem(stats, event -> {
            Player player = (Player) event.getWhoClicked();
            new AdminServerStatsGui(plugin, navigator, player).open(player);
        }));

        // Slot 13: Central Bank
        ItemStack centralBank = createEconomyItem(
                Material.GOLD_INGOT,
                "Banque Centrale",
                "Gérer la réserve monétaire",
                "Réserve: " + Format.currency(bankBalance),
                "Clic pour gérer"
        );
        gui.setItem(13, new GuiItem(centralBank, event -> {
            Player player = (Player) event.getWhoClicked();
            player.closeInventory();
            player.performCommand("tfadmin bank");
        }));

        // Slot 15: Market Trends
        ItemStack trends = createEconomyItem(
                Material.FIREWORK_STAR,
                "Tendances Marché",
                "Voir les tendances du marché",
                "Clic pour voir",
                "Clic pour voir"
        );
        gui.setItem(15, new GuiItem(trends, event -> {
            Player player = (Player) event.getWhoClicked();
            new ServerStatsGui(plugin, player).open(player);
        }));

        // --- Row 3: Management ---

        // Slot 20: Taxes
        ItemStack taxes = createEconomyItem(
                Material.DIAMOND,
                "Taxes",
                "Gérer les taxes et impôts",
                "Total collecté: " + Format.currency(totalTaxes),
                "Clic pour gérer"
        );
        gui.setItem(20, new GuiItem(taxes, event -> {
            Player player = (Player) event.getWhoClicked();
            player.closeInventory();
            player.performCommand("tfadmin taxes stats");
        }));

        // Slot 22: Events
        ItemStack events = createEconomyItem(
                Material.FIREWORK_ROCKET,
                "Événements Économiques",
                "Gérer les événements économiques",
                getEventStatus(),
                "Clic pour gérer"
        );
        gui.setItem(22, new GuiItem(events, event -> {
            Player player = (Player) event.getWhoClicked();
            player.closeInventory();
            player.performCommand("tfadmin event");
        }));

        // Slot 24: Licenses
        ItemStack licenses = createEconomyItem(
                Material.ENCHANTED_BOOK,
                "Licences",
                "Gérer les licences des joueurs",
                "",
                "Clic pour gérer"
        );
        gui.setItem(24, new GuiItem(licenses, event -> {
            Player player = (Player) event.getWhoClicked();
            player.closeInventory();
            player.performCommand("tfadmin license");
        }));

        // --- Navigation Bar (bottom right) ---
        NavigationBar.apply(gui, new NavigationBar.Config(4)
                .title("Économie")
                .onBack(() -> navigator.openMainMenu(admin))
                .showClose(true));
    }

    private String getEventStatus() {
        EconomicEventManager evtMgr = plugin.getEconomicEventManager();
        if (evtMgr != null && evtMgr.getActiveEvent() != null) {
            return "<yellow>Événement actif</yellow>";
        }
        return "<white>Aucun événement actif</white>";
    }

    private ItemStack createEconomyItem(Material material, String title, String description, String extraInfo, String actionHint) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(MiniMessage.miniMessage().deserialize("<gold>" + title + "</gold>")
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<white>" + description + "</white>")
                    .decoration(TextDecoration.ITALIC, false));
            if (extraInfo != null && !extraInfo.isEmpty()) {
                lore.add(MiniMessage.miniMessage().deserialize("<gray>" + extraInfo + "</gray>")
                        .decoration(TextDecoration.ITALIC, false));
            }
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
