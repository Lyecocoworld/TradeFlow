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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class DocsGui {

    private final TradeFlow plugin;
    private final Gui gui;

    public DocsGui(TradeFlow plugin, Player player) {
        this.plugin = plugin;

        this.gui = Gui.gui()
                .rows(4)
                .title(GuiTextCache.title("Guide du Joueur"))
                .disableAllInteractions()
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));
        BackgroundUtil.fillBackground(gui, plugin);

        buildContent(player);
    }

    private void buildContent(Player player) {
        // 1. Prix Dynamiques (Slot 10)
        addDocItem(10, Material.GOLD_INGOT, GuiTextCache.themed("<gold><b>Prix Dynamiques</b></gold>"),
                GuiTextCache.themed("<gray>Les prix changent en temps réel selon l'offre et la demande.</gray>"),
                "",
                GuiTextCache.themed("<white>• <green>Achat massif :</green> Le prix augmente 📈</white>"),
                GuiTextCache.themed("<white>• <green>Vente massive :</green> Le prix baisse 📉</white>"),
                "",
                GuiTextCache.themed("<gray>Soyez malin : achetez quand c'est bas, vendez quand c'est haut !</gray>"));

        // 2. Stock Mondial (Slot 11)
        addDocItem(11, Material.CHEST, GuiTextCache.themed("<gold><b>Stock Mondial</b></gold>"),
                GuiTextCache.themed("<gray>Les ressources du serveur sont limitées.</gray>"),
                "",
                GuiTextCache.themed("<white>• Chaque achat vide le stock global.</white>"),
                GuiTextCache.themed("<white>• Chaque vente remplit le stock global.</white>"),
                GuiTextCache.themed("<white>• Si le stock est à 0, <red>l'achat est bloqué !</red></white>"),
                "",
                GuiTextCache.themed("<gray>Le stock est réapprovisionné automatiquement <yellow>chaque semaine</yellow>.</gray>"));

        // 3. Volatilité (Slot 12)
        addDocItem(12, Material.TNT, GuiTextCache.themed("<gold><b>Volatilité & Krach</b></gold>"),
                GuiTextCache.themed("<gray>Certains items sont instables (Diamant, Netherite...).</gray>"),
                "",
                GuiTextCache.themed("<white>• Leur prix peut changer très vite.</white>"),
                GuiTextCache.themed("<white>• Risque de <red>Krach Boursier</red> si trop de ventes d'un coup.</white>"),
                "",
                GuiTextCache.themed("<gray>Surveillez les flèches de tendance (▲/▼) dans le shop.</gray>"));

        // 4. Marché Noir (Slot 14)
        addDocItem(14, Material.SPYGLASS, GuiTextCache.themed("<gold><b>Le Marché Noir</b></gold>"),
                GuiTextCache.themed("<gray>Une zone d'ombre pour les initiés.</gray>"),
                "",
                GuiTextCache.themed("<white>• <yellow>Rumeurs :</yellow> Achetez des infos pour prédire les prix.</white>"),
                GuiTextCache.themed("<white>• <yellow>Ventes Flash :</yellow> Items rares à prix cassé (-50%).</white>"),
                "",
                GuiTextCache.themed("<gray>Accessible via des commandes spéciales ou des PNJs cachés.</gray>"));

        // 5. Collect-First (Slot 15)
        addDocItem(15, Material.IRON_PICKAXE, GuiTextCache.themed("<gold><b>Progression (Collect-First)</b></gold>"),
                GuiTextCache.themed("<gray>Le mérite avant l'argent.</gray>"),
                "",
                GuiTextCache.themed("<white>• Vous ne pouvez pas acheter un item...</white>"),
                GuiTextCache.themed("<white>...tant que vous ne l'avez pas <green>découvert</green> vous-même.</white>"),
                "",
                GuiTextCache.themed("<gray>Allez miner ou farmer pour débloquer le catalogue !</gray>"));

        // 6. Taxes & Banque (Slot 16)
        addDocItem(16, Material.EMERALD, GuiTextCache.themed("<gold><b>Taxes & Banque</b></gold>"),
                GuiTextCache.themed("<gray>L'économie est régulée.</gray>"),
                "",
                GuiTextCache.themed("<white>• Une taxe est appliquée sur chaque transaction.</white>"),
                GuiTextCache.themed("<white>• L'argent collecté sert à financer les événements.</white>"),
                "",
                GuiTextCache.themed("<gray>Participez à l'effort commun !</gray>"));

        // Back (Slot 27)
        ItemStack back = new ItemStack(Material.ARROW);
        back.editMeta(m -> m.displayName(GuiTextCache.themedComponent("<white>Retour</white>")));
        gui.setItem(27, new GuiItem(back, event -> new HelpGui(plugin, player).open(player)));
    }

    private void addDocItem(int slot, Material material, String title, String... loreLines) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(MiniMessage.miniMessage().deserialize(title)
                    .decoration(TextDecoration.ITALIC, false));
            
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            for (String line : loreLines) {
                lore.add(MiniMessage.miniMessage().deserialize(line)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });
        gui.setItem(slot, new GuiItem(item));
    }

    public void open(Player player) {
        TriumphGuiAdapter.openSafe(gui, player, plugin);
    }
}
