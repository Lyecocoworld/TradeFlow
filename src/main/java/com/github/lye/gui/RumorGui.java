package com.github.lye.gui;

import com.github.lye.TradeFlow;
import com.github.lye.gameplay.rumors.RumorManager;
import com.github.lye.util.Format;
import com.github.lye.gui.framework.TriumphGuiAdapter;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RumorGui {

    private final TradeFlow plugin;
    private final RumorManager rumorManager;
    private final Gui gui;
    private final YamlConfiguration rumorConfig;

    public RumorGui(TradeFlow plugin, Player player) {
        this.plugin = plugin;
        this.rumorManager = plugin.getServices().get(RumorManager.class);
        
        File file = new File(plugin.getDataFolder(), "modules/rumors/rumors.yml");
        this.rumorConfig = YamlConfiguration.loadConfiguration(file);

        this.gui = Gui.gui()
                .rows(3)
                .title(GuiTextCache.title("Marché Noir de l'Info"))
                .disableAllInteractions()
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));
        BackgroundUtil.fillBackground(gui, plugin);

        buildContent(player);
    }

    private void buildContent(Player player) {
        // Cheap Tier (Slot 11)
        addItem(player, "cheap", Material.PAPER, 11);

        // Standard Tier (Slot 13)
        addItem(player, "standard", Material.FILLED_MAP, 13);

        // Insider Tier (Slot 15)
        addItem(player, "insider", Material.WRITTEN_BOOK, 15);

        // Back Button (Slot 18)
        ItemStack back = new ItemStack(Material.ARROW);
        back.editMeta(m -> m.displayName(
                MiniMessage.miniMessage().deserialize("<white>Retour</white>")
                        .decoration(TextDecoration.ITALIC, false)));
        gui.setItem(18, new GuiItem(back, event -> {
            new BlackMarketGui(plugin, player).open(player);
        }));
    }

    private void addItem(Player player, String tierId, Material material, int slot) {
        String path = "rumors.tiers." + tierId;
        String rawName = rumorConfig.getString(path + ".name", tierId);
        String name = MiniMessage.miniMessage().stripTags(rawName); // Strip config colors
        
        double accuracy = rumorConfig.getDouble(path + ".accuracy", 0.5);
        double price = rumorManager.getPrice(tierId);

        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(MiniMessage.miniMessage().deserialize("<gold><b>" + name + "</b></gold>")
                    .decoration(TextDecoration.ITALIC, false));
            
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<gray>Fiabilité: <gold>" + (int)(accuracy * 100) + "%</gold>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(MiniMessage.miniMessage().deserialize("<gray>Prix Actuel: <gold>" + Format.currency(price) + "</gold>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<yellow>Clic pour acheter</yellow>")
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });

        gui.setItem(slot, new GuiItem(item, event -> {
            rumorManager.purchaseRumor(player, tierId);
            // Use Folia scheduler to close inventory safely on the next tick
            player.getScheduler().run(plugin, task -> player.closeInventory(), null);
        }));
    }

    public void open(Player player) {
        TriumphGuiAdapter.openSafe(gui, player, plugin);
    }
}
