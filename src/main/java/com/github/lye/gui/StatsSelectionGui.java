package com.github.lye.gui;

import com.github.lye.TradeFlow;
import com.github.lye.gui.framework.TriumphGuiAdapter;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Menu to select between Global Stats and Organization Stats.
 */
public class StatsSelectionGui {

    private final TradeFlow plugin;
    private final Gui gui;

    public StatsSelectionGui(TradeFlow plugin, Player player) {
        this.plugin = plugin;
        this.gui = Gui.gui()
                .rows(3)
                .title(GuiTextCache.title(GuiTextCache.themed("<gold><b>Centre de Statistiques</b></gold>")))
                .disableAllInteractions()
                .create();

        BackgroundUtil.fillBackground(gui, plugin);
        build(player);
    }

    private void build(Player player) {
        // Slot 11: Statistiques Mondiales
        ItemStack serverStats = new ItemStack(Material.BEACON);
        serverStats.editMeta(meta -> {
            meta.displayName(GuiTextCache.themedComponent("<gold><b>Statistiques Mondiales</b></gold>"));
            meta.lore(java.util.List.of(
                net.kyori.adventure.text.Component.empty(),
                GuiTextCache.themedComponent("<gray>Consultez l'état global de l'économie,</gray>"),
                GuiTextCache.themedComponent("<gray>le volume d'échange et les pénuries.</gray>")
            ));
        });
        gui.setItem(11, new GuiItem(serverStats, event -> new ServerStatsGui(plugin, player).open(player)));

        // Slot 15: Statistiques d'Organisation (Banque Centrale par défaut)
        if (player.hasPermission("tradeflow.admin")) {
            ItemStack orgStats = new ItemStack(Material.NETHER_STAR);
            orgStats.editMeta(meta -> {
                meta.displayName(GuiTextCache.themedComponent("<gold><b>Banque Centrale</b></gold>"));
                meta.lore(java.util.List.of(
                    net.kyori.adventure.text.Component.empty(),
                    GuiTextCache.themedComponent("<gray>Gérez les finances et stocks</gray>"),
                    GuiTextCache.themedComponent("<gray>de la Banque Centrale.</gray>"),
                    net.kyori.adventure.text.Component.empty(),
                    GuiTextCache.themedComponent("<red>Admin Only</red>")
                ));
            });
            gui.setItem(15, new GuiItem(orgStats, event -> new OrganizationStatsGui(plugin, player).open(player)));
        } else {
            ItemStack locked = new ItemStack(Material.BARRIER);
            locked.editMeta(meta -> {
                meta.displayName(GuiTextCache.themedComponent("<red><b>Accès Restreint</b></red>"));
                meta.lore(java.util.List.of(
                    net.kyori.adventure.text.Component.empty(),
                    GuiTextCache.themedComponent("<gray>Vous ne possédez pas d'organisation.</gray>"),
                    GuiTextCache.themedComponent("<gray>Accès Banque Centrale réservé.</gray>")
                ));
            });
            gui.setItem(15, new GuiItem(locked));
        }

        // Slot 18: Retour
        ItemStack back = new ItemStack(Material.ARROW);
        back.editMeta(m -> m.displayName(GuiTextCache.themedComponent("<gold><b>Retour</b></gold>")));
        gui.setItem(18, new GuiItem(back, event -> new UtilityGui(plugin, player).open(player)));
    }

    public void open(Player player) {
        TriumphGuiAdapter.openSafe(gui, player, plugin);
    }
}
