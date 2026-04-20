package com.github.lye.gui;

import com.github.lye.TradeFlow;
import com.github.lye.data.CentralBankStockManager;
import com.github.lye.data.Database;
import com.github.lye.gui.framework.TriumphGuiAdapter;
import com.github.lye.pricing.service.PriceService;
import com.github.lye.util.Format;
import com.github.lye.util.EconomyUtil;
import com.github.lye.data.Shop;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Statistics GUI for the Player's Organization (or Central Bank by default).
 */
public class OrganizationStatsGui {

    private final TradeFlow plugin;
    private final Gui gui;

    public OrganizationStatsGui(TradeFlow plugin, Player player) {
        this.plugin = plugin;
        this.gui = Gui.gui()
                .rows(3)
                .title(GuiTextCache.title(GuiTextCache.themed("<gold><b>Statistiques d'Organisation</b></gold>")))
                .disableAllInteractions()
                .create();

        BackgroundUtil.fillBackground(gui, plugin);
        buildContent(player);
    }

    private void buildContent(Player player) {
        String orgName = "Banque Centrale";
        
        // 1. Liquid Cash (Vault) - Unified check
        double cash = EconomyUtil.getCentralBankBalance(plugin);
        
        // 2. Initial Capital (Config) - Base for ROI
        CentralBankStockManager bankMgr = plugin.getServices().get(CentralBankStockManager.class);
        double initial = plugin.getServices().get(com.github.lye.config.settings.IPluginSettings.class).getInitialCapital();
        if (initial < 0) {
            double startupLiquidity = bankMgr.calculateRequiredLiquidity();
            initial = startupLiquidity + startupLiquidity;
        }

        // 3. Stock Value Calculation
        double totalStockValue = 0;
        int totalUnits = 0;
        
        // Get latest prices from Service
        com.github.lye.pricing.model.PriceSnapshot snapshot = plugin.getServices().get(PriceService.class).getCurrentSnapshot();
        Map<String, Shop> shops = plugin.getServices().get(Database.class).getShops();
        
        for (Shop shop : shops.values()) {
            int currentStock = bankMgr.getCurrentStock(shop);
            String name = shop.getName();
            
            com.github.lye.pricing.model.ItemId id = new com.github.lye.pricing.model.ItemId(name);
            double dynPrice = snapshot.getPrice(id).orElse(0.0);
            double finalPrice = (dynPrice > 0 && Double.isFinite(dynPrice)) ? dynPrice : shop.getPrice();
            
            totalUnits += currentStock;
            if (finalPrice > 0 && Double.isFinite(finalPrice)) {
                totalStockValue += (currentStock * finalPrice);
            }
        }

        // Total Net Worth

                double netWorth = cash + totalStockValue;

        

                final double fCash = cash;

                final double fInitial = initial;

                final double fStockValue = totalStockValue;

                final double fNetWorth = netWorth;

                final int fTotalUnits = totalUnits;

        

                // Slot 11: Trésorerie & Solvabilité

                ItemStack treasuryItem = new ItemStack(Material.GOLD_INGOT);

                treasuryItem.editMeta(meta -> {

                    meta.displayName(GuiTextCache.themedComponent("<gold><b>Finances de l'Organisation</b></gold>"));

                    List<Component> lore = new ArrayList<>();

                    lore.add(Component.empty());

                    lore.add(GuiTextCache.themedComponent("<gray>Organisation : <yellow>" + orgName + "</yellow>"));

                    lore.add(GuiTextCache.themedComponent("<gray>Capital Initial : <white>" + Format.currency(fInitial) + "</white>"));

                    lore.add(GuiTextCache.themedComponent("<gray>Liquidités (Cash) : <green>" + Format.currency(fCash) + "</green>"));

                    lore.add(Component.empty());

                    lore.add(GuiTextCache.themedComponent("<gray>Valeur Totale (Net Worth) :</gray>"));

                    lore.add(GuiTextCache.themedComponent("<gold><b>" + Format.compactNumber(fNetWorth) + " $</b></gold>"));

                    meta.lore(lore);

                });

                gui.setItem(11, new GuiItem(treasuryItem));

        

                // Slot 13: Inventaire & Actifs

                ItemStack stockItem = new ItemStack(Material.CHEST_MINECART);

                stockItem.editMeta(meta -> {

                    meta.displayName(GuiTextCache.themedComponent("<gold><b>Actifs & Stocks Virtuels</b></gold>"));

                    List<Component> lore = new ArrayList<>();

                    lore.add(Component.empty());

                    lore.add(GuiTextCache.themedComponent("<gray>Volume de Stock : <yellow>" + Format.compactNumber(fTotalUnits) + " unités</yellow>"));

                    lore.add(GuiTextCache.themedComponent("<gray>Valeur Marchande : <green>" + Format.compactNumber(fStockValue) + " $</green>"));

                    lore.add(Component.empty());

                    lore.add(GuiTextCache.themedComponent("<yellow>Basé sur les prix du marché actuel.</yellow>"));

                    meta.lore(lore);

                });

                gui.setItem(13, new GuiItem(stockItem));

        

                // Slot 15: Performance & Ratio

                ItemStack performance = new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE);

                performance.editMeta(meta -> {

                    meta.displayName(GuiTextCache.themedComponent("<gold><b>Indice de Santé</b></gold>"));

                    List<Component> lore = new ArrayList<>();

                    lore.add(Component.empty());

                    

                    double ROI = 0;

                    if (fInitial > 0.01) {

                        ROI = ((fNetWorth - fInitial) / fInitial) * 100;

                    }

                    

                    if (Double.isInfinite(ROI) || Double.isNaN(ROI)) {

                        ROI = 0;

                    }

        

                    String roiColor = ROI >= 0 ? "<green>" : "<red>";

                    String roiSign = ROI >= 0 ? "+" : "";

        

                    lore.add(GuiTextCache.themedComponent("<gray>Croissance Globale : " + roiColor + roiSign + String.format("%.2f", ROI) + "%</gray>"));

                    

                    double ratio = 0;

                    if (fStockValue > 0.1) {

                         ratio = fCash / fStockValue;

                    } else if (fCash > 0 && fTotalUnits == 0) {

                         // No stock at all but we have cash? Ratio is technically infinite safety, but let's show 0 health

                         ratio = 0;

                    }

                    if (Double.isInfinite(ratio) || Double.isNaN(ratio)) ratio = 0;

        

                    lore.add(GuiTextCache.themedComponent("<gray>Ratio Cash/Stock : <white>" + String.format("%.2f", ratio) + "</white>"));
            lore.add(Component.empty());
            lore.add(GuiTextCache.themedComponent("<yellow>Analyse financière complète.</yellow>"));
            meta.lore(lore);
        });
        gui.setItem(15, new GuiItem(performance));

        // Back Button (Slot 18)
        ItemStack back = new ItemStack(Material.ARROW);
        back.editMeta(m -> m.displayName(GuiTextCache.themedComponent("<gold><b>Retour</b></gold>")));
        gui.setItem(18, new GuiItem(back, event -> new StatsSelectionGui(plugin, player).open(player)));
    }

    public void open(Player player) {
        TriumphGuiAdapter.openSafe(gui, player, plugin);
    }
}
