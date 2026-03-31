package com.github.lye.gui;

import com.github.lye.TradeFlow;
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
import java.util.Set;
import java.util.UUID;

/**
 * Admin Server Stats GUI - Same as ServerStatsGui but returns to admin menu.
 *
 * @author lye
 * @since 0.1
 */
public class AdminServerStatsGui {

    private final TradeFlow plugin;
    private final AdminNavigator navigator;
    private final Player player;
    private final Gui gui;

    public AdminServerStatsGui(TradeFlow plugin, AdminNavigator navigator, Player player) {
        this.plugin = plugin;
        this.navigator = navigator;
        this.player = player;

        this.gui = Gui.gui()
                .rows(3)
                .title(GuiTextCache.title("Statistiques Mondiales"))
                .disableAllInteractions()
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));
        BackgroundUtil.fillBackground(gui, plugin);
        buildContent();
    }

    private void buildContent() {
        double bankBalance = com.github.lye.util.EconomyUtil.getCentralBankBalance(plugin);
        com.github.lye.data.CentralBankStockManager.EconomicPolicy policy = plugin.getCentralBankStockManager().getCurrentPolicy();

        double inflation = plugin.getPricingManager().getGlobalInflationIndex();
        String inflationColor = inflation >= 0 ? "<red>" : "<green>";
        String inflationSign = inflation >= 0 ? "+" : "";

        Map<String, com.github.lye.data.Transaction> tx = plugin.getLoadedTransactions();
        int totalTx = tx != null ? tx.size() : 0;
        double totalVolume = 0.0;
        int buys = 0;
        int sells = 0;
        Set<UUID> uniquePlayers = new java.util.HashSet<>();
        if (tx != null) {
            for (com.github.lye.data.Transaction t : tx.values()) {
                if (t == null) continue;
                totalVolume += t.getPrice() * t.getAmount();
                if (t.getPosition() == com.github.lye.data.Transaction.TransactionType.BUY) buys++;
                if (t.getPosition() == com.github.lye.data.Transaction.TransactionType.SELL) sells++;
                if (t.getPlayer() != null) uniquePlayers.add(t.getPlayer());
            }
        }

        List<String> publicOrders = new ArrayList<>();
        if (plugin.getLoadedShops() != null) {
            for (com.github.lye.data.Shop shop : plugin.getLoadedShops().values()) {
                if (shop != null && plugin.getCentralBankStockManager().isPublicOrderActive(shop)) {
                    publicOrders.add(shop.getName());
                }
            }
        }

        int shopCount = plugin.getLoadedShops() != null ? plugin.getLoadedShops().size() : 0;
        int licenses = plugin.getLicenseManager() != null ? plugin.getLicenseManager().getAllDefinitions().size() : 0;

        final double fBankBalance = bankBalance;
        final String fPolicy = policy.getDisplay();
        final double fInflation = inflation;
        final String fInfColor = inflationColor;
        final String fInfSign = inflationSign;
        final int fTotalTx = totalTx;
        final double fTotalVolume = totalVolume;
        final List<String> fPublicOrders = publicOrders;
        final int fShopCount = shopCount;
        final int fUniquePlayers = uniquePlayers.size();
        final int fLicenses = licenses;
        final int fBuys = buys;
        final int fSells = sells;

        // Slot 11: Banque Centrale
        ItemStack bankItem = new ItemStack(Material.GOLD_BLOCK);
        bankItem.editMeta(meta -> {
            meta.displayName(MiniMessage.miniMessage().deserialize("<gold>Banque Centrale</gold>")
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<white>Trésorerie: <yellow>" + com.github.lye.util.Format.currency(fBankBalance) + "</yellow></white>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(MiniMessage.miniMessage().deserialize("<white>Politique: " + fPolicy + "</white>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(MiniMessage.miniMessage().deserialize("<white>Indice TradeFlow: " + fInfColor + fInfSign + String.format("%.2f", fInflation * 100) + "%" + "</fInfColor></white>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<yellow>La Banque Centrale garantit la valeur de la monnaie.</yellow>")
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        gui.setItem(11, new GuiItem(bankItem));

        // Slot 13: Marché
        ItemStack marketItem = new ItemStack(Material.WRITABLE_BOOK);
        marketItem.editMeta(meta -> {
            meta.displayName(MiniMessage.miniMessage().deserialize("<gold>Marché Mondial</gold>")
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<white>Volume Transactions: <yellow>" + fTotalTx + "</yellow></white>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(MiniMessage.miniMessage().deserialize("<white>Volume cumulé: <yellow>" + com.github.lye.util.Format.currency(fTotalVolume) + "</yellow></white>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(MiniMessage.miniMessage().deserialize("<white>Commandes Publiques: <yellow>" + fPublicOrders.size() + "</yellow></white>")
                    .decoration(TextDecoration.ITALIC, false));

            if (!fPublicOrders.isEmpty()) {
                lore.add(Component.empty());
                lore.add(MiniMessage.miniMessage().deserialize("<red>Besoins urgents détectés!</red>")
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.empty());
                lore.add(MiniMessage.miniMessage().deserialize("<green>Le marché est stable.</green>")
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        });
        gui.setItem(13, new GuiItem(marketItem));

        // Slot 15: Indicateurs
        ItemStack trend = new ItemStack(Material.PAPER);
        trend.editMeta(meta -> {
            meta.displayName(MiniMessage.miniMessage().deserialize("<gold>Indicateurs Globaux</gold>")
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<white>Shops actifs: <yellow>" + fShopCount + "</yellow></white>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(MiniMessage.miniMessage().deserialize("<white>Joueurs actifs: <yellow>" + fUniquePlayers + "</yellow></white>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(MiniMessage.miniMessage().deserialize("<white>Licences: <yellow>" + fLicenses + "</yellow></white>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(MiniMessage.miniMessage().deserialize("<white>Achats: <green>" + fBuys + "</green> | Ventes: <red>" + fSells + "</red></white>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<yellow>Données en temps réel</yellow>")
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        gui.setItem(15, new GuiItem(trend));

        // Back Button (Navigation Bar - bottom right for 3 rows = slot 26)
        NavigationBar.apply(gui, new NavigationBar.Config(3)
                .title("Stats Serveur")
                .onBack(() -> navigator.openMainMenu(player))
                .showClose(true));
    }

    public void open(Player player) {
        gui.open(player);
    }
}
