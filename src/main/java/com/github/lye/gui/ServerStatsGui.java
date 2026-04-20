package com.github.lye.gui;

import com.github.lye.TradeFlow;
import com.github.lye.data.CentralBankStockManager;
import com.github.lye.data.Database;
import com.github.lye.data.Shop;
import com.github.lye.data.Transaction;
import com.github.lye.gui.framework.TriumphGuiAdapter;
import com.github.lye.license.LicenseManager;
import com.github.lye.pricing.PricingManager;
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
 * Enhanced Server Stats GUI.
 */
public class ServerStatsGui {

    private final TradeFlow plugin;
    private final Gui gui;

    public ServerStatsGui(TradeFlow plugin, Player player) {
        this.plugin = plugin;
        this.gui = Gui.gui()
                .rows(3)
                .title(GuiTextCache.title(GuiTextCache.themed("<gold><b>Statistiques Mondiales</b></gold>")))
                .disableAllInteractions()
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));
        BackgroundUtil.fillBackground(gui, plugin);
        buildContent(player);
    }

    private void buildContent(Player player) {
        CentralBankStockManager bankManager = plugin.getServices().get(CentralBankStockManager.class);
        double bankBalance = com.github.lye.util.EconomyUtil.getCentralBankBalance(plugin);
        CentralBankStockManager.EconomicPolicy policy = bankManager.getCurrentPolicy();
        
        double inflation = plugin.getServices().get(PricingManager.class).getGlobalInflationIndex();
        String inflationColor = inflation >= 0 ? "<red>" : "<green>";
        String inflationSign = inflation >= 0 ? "+" : "";

        Map<String, Transaction> tx = plugin.getServices().get(Database.class).getTransactions();
        int totalTx = tx != null ? tx.size() : 0;
        double totalVolume = 0.0;
        int buys = 0;
        int sells = 0;
        Set<UUID> uniquePlayers = new java.util.HashSet<>();
        if (tx != null) {
            for (Transaction t : tx.values()) {
                if (t == null) continue;
                totalVolume += t.getPrice() * t.getAmount();
                if (t.getPosition() == Transaction.TransactionType.BUY) buys++;
                if (t.getPosition() == Transaction.TransactionType.SELL) sells++;
                if (t.getPlayer() != null) uniquePlayers.add(t.getPlayer());
            }
        }

        List<String> publicOrders = new ArrayList<>();
        Map<String, Shop> shops = plugin.getServices().get(Database.class).getShops();
        if (shops != null) {
            for (Shop shop : shops.values()) {
                if (shop != null && bankManager.isPublicOrderActive(shop)) {
                    publicOrders.add(shop.getName());
                }
            }
        }

        int shopCount = shops.size();
        LicenseManager licenseManager = plugin.getServices().get(LicenseManager.class);
        int licenses = licenseManager != null ? licenseManager.getAllDefinitions().size() : 0;

        // Final copies for lambdas
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
            meta.displayName(GuiTextCache.themedComponent("<gold><b>Banque Centrale</b></gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(GuiTextCache.themedComponent("<gray>Trésorerie : <yellow>" + com.github.lye.util.Format.currency(fBankBalance) + "</yellow>"));
            lore.add(GuiTextCache.themedComponent("<gray>Politique : " + fPolicy));
            lore.add(GuiTextCache.themedComponent("<gray>Indice TradeFlow : " + fInfColor + "<b>" + fInfSign + String.format("%.2f", fInflation * 100) + "%</b></gray>"));
            lore.add(Component.empty());
            lore.add(GuiTextCache.themedComponent("<yellow>La Banque Centrale garantit la valeur de la monnaie.</yellow>"));
            meta.lore(lore);
        });
        gui.setItem(11, new GuiItem(bankItem));

        // Slot 13: Marché & Commandes
        ItemStack marketItem = new ItemStack(Material.WRITABLE_BOOK);
        marketItem.editMeta(meta -> {
            meta.displayName(GuiTextCache.themedComponent("<gold><b>Marché Mondial</b></gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(GuiTextCache.themedComponent("<gray>Volume de Transactions : <yellow>" + fTotalTx + "</yellow>"));
            lore.add(GuiTextCache.themedComponent("<gray>Volume cumulé : <yellow>" + com.github.lye.util.Format.currency(fTotalVolume) + "</yellow>"));
            lore.add(GuiTextCache.themedComponent("<gray>Commandes Publiques : <green>" + fPublicOrders.size() + "</green>"));
            
            if (!fPublicOrders.isEmpty()) {
                lore.add(Component.empty());
                lore.add(GuiTextCache.themedComponent("<red><b>Besoins urgents détectés !</b></red>"));
                lore.add(GuiTextCache.themedComponent("<yellow>Clic pour ouvrir le Tableau des Commandes</yellow>"));
            } else {
                lore.add(Component.empty());
                lore.add(GuiTextCache.themedComponent("<green>Le marché est stable.</green>"));
            }
            meta.lore(lore);
        });
        gui.setItem(13, new GuiItem(marketItem, event -> new PublicOrderGui(plugin, player).open(player)));

        // Slot 15: Indicateurs
        ItemStack trend = new ItemStack(Material.PAPER);
        trend.editMeta(meta -> {
            meta.displayName(GuiTextCache.themedComponent("<gold><b>Indicateurs Globaux</b></gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(GuiTextCache.themedComponent("<gray>Shops actifs : <yellow>" + fShopCount + "</yellow>"));
            lore.add(GuiTextCache.themedComponent("<gray>Joueurs actifs : <yellow>" + fUniquePlayers + "</yellow>"));
            lore.add(GuiTextCache.themedComponent("<gray>Licences définies : <yellow>" + fLicenses + "</yellow>"));
            lore.add(GuiTextCache.themedComponent("<gray>Buy total : <green>" + fBuys + "</green> | Sell total : <red>" + fSells + "</red>"));
            lore.add(Component.empty());
            lore.add(GuiTextCache.themedComponent("<yellow>Chiffres actualisés en temps réel.</yellow>"));
            meta.lore(lore);
        });
        gui.setItem(15, new GuiItem(trend));

        // Back Button (Slot 18)
        ItemStack back = new ItemStack(Material.ARROW);
        back.editMeta(m -> m.displayName(
                GuiTextCache.themedComponent("<gold><b>Retour</b></gold>")));
        gui.setItem(18, new GuiItem(back, event -> new StatsSelectionGui(plugin, player).open(player)));
    }

    public void open(Player player) {
        TriumphGuiAdapter.openSafe(gui, player, plugin);
    }
}
