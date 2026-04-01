package com.github.lye.gui;

import com.github.lye.TradeFlow;
import com.github.lye.data.Shop;
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
 * Public Order Board (Job Board).
 * Lists urgent needs of the Central Bank and allows quick delivery.
 */
public class PublicOrderGui {

    private final TradeFlow plugin;
    private final Gui gui;

    public PublicOrderGui(TradeFlow plugin, Player player) {
        this.plugin = plugin;
        this.gui = Gui.gui()
                .rows(5)
                .title(GuiTextCache.title("<gold><b>Tableau des Commandes Publiques</b></gold>"))
                .disableAllInteractions()
                .create();

        BackgroundUtil.fillBackground(gui, plugin);
        buildContent(player);
    }

    private void buildContent(Player player) {
        boolean isInsider = plugin.getReputationManager().isInsider(player.getUniqueId());
        int slot = 10;

        for (Shop shop : plugin.getLoadedShops().values()) {
            if (shop.getGlobalStockLimit() <= 0) continue;

            int currentStock = plugin.getCentralBankStockManager().getCurrentStock(shop);
            int idealStock = shop.getGlobalStockLimit();
            
            boolean isCritical = currentStock < (idealStock * 0.25);
            boolean isApproaching = currentStock < (idealStock * 0.45);

            if (isCritical || (isInsider && isApproaching)) {
                if (slot > 34) break; // Page limit simple implementation
                if (slot % 9 == 8) slot += 2;

                Material mat = Material.matchMaterial(shop.getName().toUpperCase());
                if (mat == null) mat = Material.PAPER;

                ItemStack item = new ItemStack(mat);
                boolean finalIsCritical = isCritical;
                item.editMeta(meta -> {
                    String status = finalIsCritical ? "<red><b>[CRITIQUE]</b></red>" : "<blue><b>[ANTICIPÉ]</b></blue>";
                    meta.displayName(MiniMessage.miniMessage().deserialize(status + " <gold>" + shop.getName() + "</gold>")
                            .decoration(TextDecoration.ITALIC, false));
                    
                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.empty());
                    lore.add(MiniMessage.miniMessage().deserialize("<gray>Stock Actuel : <yellow>" + Format.compactNumber(currentStock) + " / " + Format.compactNumber(idealStock) + "</yellow>")
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(MiniMessage.miniMessage().deserialize("<gray>Récompense : <green>Prix de vente +20%</green>")
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.empty());
                    lore.add(MiniMessage.miniMessage().deserialize("<yellow>Clic pour livrer l'item en main</yellow>")
                            .decoration(TextDecoration.ITALIC, false));
                    meta.lore(lore);
                });

                gui.setItem(slot++, new GuiItem(item, event -> {
                    // Quick Delivery Logic
                    ItemStack hand = player.getInventory().getItemInMainHand();
                    if (hand.getType() != item.getType()) {
                        player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Vous n'avez pas le bon item en main !</red>"));
                        return;
                    }
                    // Trigger normal sell with bonus logic already in PurchaseUtil
                    plugin.getPurchaseUtil().sellItemStack(hand, player);
                    player.getInventory().setItemInMainHand(null);
                    new PublicOrderGui(plugin, player).open(player); // Refresh
                }));
            }
        }

        // Back Button
        ItemStack back = new ItemStack(Material.ARROW);
        back.editMeta(m -> m.displayName(MiniMessage.miniMessage().deserialize("<gold><b>Retour</b></gold>")
                .decoration(TextDecoration.ITALIC, false)));
        gui.setItem(40, new GuiItem(back, event -> new ServerStatsGui(plugin, player).open(player)));
    }

    public void open(Player player) {
        gui.open(player);
    }
}