package com.github.lye.gui;

import com.github.lye.TradeFlow;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class HelpGui {

    private final TradeFlow plugin;
    private final Gui gui;

    public HelpGui(TradeFlow plugin, Player player) {
        this.plugin = plugin;

        this.gui = Gui.gui()
                .rows(3)
                .title(GuiTextCache.title("Aide & Documentation"))
                .disableAllInteractions()
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));
        BackgroundUtil.fillBackground(gui, plugin);

        buildContent(player);
    }

    private void buildContent(Player player) {
        // 1. Guide Interactif (Slot 11)
        ItemStack guideIcon = new ItemStack(Material.KNOWLEDGE_BOOK);
        guideIcon.editMeta(meta -> {
            meta.displayName(GuiTextCache.themedComponent("<gold><b>Guide du Joueur</b></gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(GuiTextCache.themedComponent("<gray>Tout savoir sur le fonctionnement</gray>"));
            lore.add(GuiTextCache.themedComponent("<gray>de l'économie du serveur.</gray>"));
            lore.add(Component.empty());
            lore.add(GuiTextCache.themedComponent("<green>✔ Prix Dynamiques</green>"));
            lore.add(GuiTextCache.themedComponent("<green>✔ Stock Mondial</green>"));
            lore.add(GuiTextCache.themedComponent("<green>✔ Marché Noir</green>"));
            lore.add(Component.empty());
            lore.add(GuiTextCache.themedComponent("<yellow>Clic pour ouvrir</yellow>"));
            meta.lore(lore);
        });
        gui.setItem(11, new GuiItem(guideIcon, event -> {
            new DocsGui(plugin, player).open(player);
        }));

        // 2. Wiki Web (Slot 15) - Coming Soon
        ItemStack wikiIcon = new ItemStack(Material.PAPER);
        wikiIcon.editMeta(meta -> {
            meta.displayName(GuiTextCache.themedComponent("<aqua><b>Wiki Officiel</b></aqua>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(GuiTextCache.themedComponent("<gray>Documentation détaillée sur le web.</gray>"));
            lore.add(Component.empty());
            lore.add(GuiTextCache.themedComponent("<red>En construction (WIP)</red>"));
            meta.lore(lore);
        });
        gui.setItem(15, new GuiItem(wikiIcon, event -> {
            // Placeholder action
        }));

        // Navigation bar at bottom (row 3)
        // Back -> Main Menu, Close -> Exit
        NavigationBar.apply(gui, new NavigationBar.Config(3)
                .onBack(() -> {
                    new MainShopGui(plugin, new GuiNavigator(plugin), new com.github.lye.gui.state.PlayerShopState(player.getUniqueId()), plugin.getTradeLogger())
                        .open(player);
                })
        );
    }

    public void open(Player player) {
        gui.open(player);
    }
}
