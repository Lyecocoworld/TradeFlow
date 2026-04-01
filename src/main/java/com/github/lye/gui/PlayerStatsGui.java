package com.github.lye.gui;

import com.github.lye.TradeFlow;
import com.github.lye.license.License;
import com.github.lye.license.PlayerLicense;
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
 * Simple placeholder GUI for player stats (WIP) with normalized styling.
 */
public class PlayerStatsGui {

    private final TradeFlow plugin;
    private final Gui gui;

    public PlayerStatsGui(TradeFlow plugin, Player player) {
        this.plugin = plugin;

        this.gui = Gui.gui()
                .rows(3)
                .title(GuiTextCache.title(GuiTextCache.themed("<gold><b>Statistiques joueur</b></gold>")))
                .disableAllInteractions()
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));
        BackgroundUtil.fillBackground(gui, plugin);

        buildContent(player);
    }

    private void buildContent(Player player) {
        double balance = plugin.getEconomy() != null ? plugin.getEconomy().getBalance(player) : 0.0;
        PlayerLicense active = plugin.getLicenseManager() != null ? plugin.getLicenseManager().getActiveLicense(player) : null;
        License activeDef = active != null && plugin.getLicenseManager() != null
                ? plugin.getLicenseManager().getLicenseDefinition(active.getLicenseId())
                : null;
        long now = System.currentTimeMillis();

        Map<String, com.github.lye.data.Transaction> tx = plugin.getLoadedTransactions();
        int totalTx = 0;
        int buys = 0;
        int sells = 0;
        double spent = 0.0;
        double earned = 0.0;
        if (tx != null) {
            for (com.github.lye.data.Transaction t : tx.values()) {
                if (t == null || t.getPlayer() == null || !t.getPlayer().equals(player.getUniqueId())) continue;
                totalTx++;
                double volume = t.getPrice() * t.getAmount();
                if (t.getPosition() == com.github.lye.data.Transaction.TransactionType.BUY) {
                    buys++;
                    spent += volume;
                } else if (t.getPosition() == com.github.lye.data.Transaction.TransactionType.SELL) {
                    sells++;
                    earned += volume;
                }
            }
        }
        double net = earned - spent;
        String netColor = net >= 0 ? "<green>" : "<red>";
        
        // Final copies for lambdas
        final int fTotalTx = totalTx;
        final int fBuys = buys;
        final int fSells = sells;
        final double fSpent = spent;
        final double fEarned = earned;
        final double fNet = net;
        final String fNetColor = netColor;

        // Slot 11: Profil
        ItemStack profile = new ItemStack(Material.PLAYER_HEAD);
        profile.editMeta(meta -> {
            meta.displayName(GuiTextCache.themedComponent("<gold><b>Profil</b></gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(GuiTextCache.themedComponent("<gray>Joueur: <yellow>" + player.getName() + "</yellow>"));
            lore.add(GuiTextCache.themedComponent("<gray>Solde: <yellow>" + com.github.lye.util.Format.currency(balance) + "</yellow>"));
            lore.add(Component.empty());
            lore.add(GuiTextCache.themedComponent("<gray>Transactions: <yellow>" + fTotalTx + "</yellow>"));
            lore.add(GuiTextCache.themedComponent("<gray>Buy: <green>" + fBuys + "</green> <gray>| Sell: <red>" + fSells + "</red>"));
            lore.add(Component.empty());
            lore.add(GuiTextCache.themedComponent("<yellow>Historique detaille bientot</yellow>"));
            meta.lore(lore);
        });
        gui.setItem(11, new GuiItem(profile));

        // Slot 13: Licence active
        ItemStack license = new ItemStack(Material.WRITABLE_BOOK);
        license.editMeta(meta -> {
            meta.displayName(GuiTextCache.themedComponent("<gold><b>Licence active</b></gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            if (activeDef != null) {
                lore.add(GuiTextCache.themedComponent("<gray>Nom: <yellow>" + activeDef.getName() + "</yellow>"));
                long remaining = Math.max(0, active.getExpiresAt() - now);
                lore.add(GuiTextCache.themedComponent("<gray>Expiration: <yellow>" + com.github.lye.util.Format.formatDuration(remaining) + "</yellow>"));
            } else {
                lore.add(GuiTextCache.themedComponent("<red>Aucune licence active</red>"));
            }
            lore.add(Component.empty());
            lore.add(GuiTextCache.themedComponent("<yellow>Gestion detaillee bientot</yellow>"));
            meta.lore(lore);
        });
        gui.setItem(13, new GuiItem(license));

        // Slot 15: Performance
        ItemStack perf = new ItemStack(Material.GOLD_INGOT);
        perf.editMeta(meta -> {
            meta.displayName(GuiTextCache.themedComponent("<gold><b>Performance marche</b></gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(GuiTextCache.themedComponent("<gray>Revenu: <green>" + com.github.lye.util.Format.currency(fEarned) + "</green>"));
            lore.add(GuiTextCache.themedComponent("<gray>Depenses: <red>" + com.github.lye.util.Format.currency(fSpent) + "</red>"));
            lore.add(GuiTextCache.themedComponent("<gray>Net: " + fNetColor + com.github.lye.util.Format.currency(fNet) + "</gray>"));
            lore.add(Component.empty());
            lore.add(GuiTextCache.themedComponent("<yellow>Stats completes a venir</yellow>"));
            meta.lore(lore);
        });
        gui.setItem(15, new GuiItem(perf));

        // Back Button (Slot 18)
        ItemStack back = new ItemStack(Material.ARROW);
        back.editMeta(m -> m.displayName(
                GuiTextCache.themedComponent("<gold><b>Retour aux outils</b></gold>")));
        gui.setItem(18, new GuiItem(back, event -> new UtilityGui(plugin, player).open(player)));
    }

    public void open(Player player) {
        gui.open(player);
    }
}
