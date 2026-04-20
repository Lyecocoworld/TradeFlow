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

public class UtilityGui {

    private final TradeFlow plugin;
    private final Gui gui;

    public UtilityGui(TradeFlow plugin, Player player) {
        this.plugin = plugin;

        this.gui = Gui.gui()
                .rows(3)
                .title(GuiTextCache.title(GuiTextCache.themed("<gold><b>Outils utilitaires</b></gold>")) )
                .disableAllInteractions()
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));
        BackgroundUtil.fillBackground(gui, plugin);

        buildContent(player);
    }

    private void buildContent(Player player) {
        // 1. Licences (Slot 11)
        ItemStack licenseIcon = new ItemStack(Material.WRITABLE_BOOK);
        licenseIcon.editMeta(meta -> {
            meta.displayName(GuiTextCache.themedComponent("<gold><b>Licences commerciales</b></gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(GuiTextCache.themedComponent("<gray>Gerer vos permis de vente</gray>"));
            lore.add(GuiTextCache.themedComponent("<gray>et vos avantages fiscaux.</gray>"));
            lore.add(Component.empty());
            lore.add(GuiTextCache.themedComponent("<yellow>Clic pour acceder</yellow>"));
            meta.lore(lore);
        });
        gui.setItem(11, new GuiItem(licenseIcon, event -> new LicenseGui(plugin, player).open(player)));

        // 2. Statistiques (Slot 13)
        ItemStack statsIcon = new ItemStack(Material.BOOK);
        statsIcon.editMeta(meta -> {
            meta.displayName(GuiTextCache.themedComponent("<gold><b>Centre de Statistiques</b></gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(GuiTextCache.themedComponent("<gray>Analyse du marché mondial</gray>"));
            lore.add(GuiTextCache.themedComponent("<gray>et de votre organisation.</gray>"));
            lore.add(Component.empty());
            lore.add(GuiTextCache.themedComponent("<yellow>Clic pour ouvrir le centre</yellow>"));
            meta.lore(lore);
        });
        gui.setItem(13, new GuiItem(statsIcon, event -> new StatsSelectionGui(plugin, player).open(player)));

        // 3. Profil Joueur (Slot 15) - WIP
        ItemStack profileIcon = new ItemStack(Material.PLAYER_HEAD);
        profileIcon.editMeta(meta -> {
            meta.displayName(GuiTextCache.themedComponent("<gold><b>Mon profil</b></gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(GuiTextCache.themedComponent("<gray>Historique, niveau, reputation.</gray>"));
            lore.add(Component.empty());
            lore.add(GuiTextCache.themedComponent("<yellow>Clic pour ouvrir</yellow>"));
            meta.lore(lore);
        });
        gui.setItem(15, new GuiItem(profileIcon, event -> new PlayerStatsGui(plugin, player).open(player)));

        // Back Button (Slot 18)
        ItemStack back = new ItemStack(Material.ARROW);
        back.editMeta(m -> m.displayName(
                GuiTextCache.themedComponent("<gold><b>Retour au menu principal</b></gold>")));
        gui.setItem(18, new GuiItem(back, event -> plugin.getServices().get(com.github.lye.gui.GuiNavigator.class).openMain(player)));
    }

    public void open(Player player) {
        TriumphGuiAdapter.openSafe(gui, player, plugin);
    }
}
