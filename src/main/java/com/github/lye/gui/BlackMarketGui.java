package com.github.lye.gui;

import com.github.lye.TradeFlow;
import com.github.lye.gameplay.rumors.BrokerReputation;
import com.github.lye.gameplay.rumors.RumorManager;
import com.github.lye.util.Format;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class BlackMarketGui {

    private final TradeFlow plugin;
    private final Gui gui;

    public BlackMarketGui(TradeFlow plugin, Player player) {
        this.plugin = plugin;

        this.gui = Gui.gui()
                .rows(6)
                .title(GuiTextCache.title("Marché Noir"))
                .disableAllInteractions()
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));
        BackgroundUtil.fillBackground(gui, plugin);

        buildMenu(player);
        buildReputationDisplay(player);
        addNavigation(player);
    }

    private void buildMenu(Player player) {
        // 1. Rumors & Intel (Slot 11)
        ItemStack rumorIcon = new ItemStack(Material.FILLED_MAP);
        rumorIcon.editMeta(meta -> {
            meta.displayName(MiniMessage.miniMessage().deserialize("<gold><b>Réseau d'Information</b></gold>")
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<gray>Achetez des rumeurs et anticipez</gray>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(MiniMessage.miniMessage().deserialize("<gray>les mouvements du marché.</gray>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<yellow>Clic pour accéder</yellow>")
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });
        gui.setItem(11, new GuiItem(rumorIcon, event -> {
            new RumorGui(plugin, player).open(player);
        }));

        // 2. Flash Sales (Slot 13)
        ItemStack flashIcon = new ItemStack(Material.GOLD_INGOT);
        flashIcon.editMeta(meta -> {
            meta.displayName(MiniMessage.miniMessage().deserialize("<gold><b>Ventes Flash</b></gold>")
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<gray>Offres limitées sur des items</gray>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(MiniMessage.miniMessage().deserialize("<gray>rares ou en surplus.</gray>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<yellow>Clic pour voir les offres</yellow>")
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        gui.setItem(13, new GuiItem(flashIcon, event -> {
            new FlashSaleGui(plugin, player).open(player);
        }));

        // 3. Shadow Contracts - Coming Soon (Slot 15)
        ItemStack contractIcon = new ItemStack(Material.WRITABLE_BOOK);
        contractIcon.editMeta(meta -> {
            meta.displayName(MiniMessage.miniMessage().deserialize("<gold><b>Contrats de l'Ombre</b></gold>")
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<gray>Missions spéciales de contrebande</gray>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(MiniMessage.miniMessage().deserialize("<gray>et livraisons risquées.</gray>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<red>Bientôt disponible...</red>")
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        gui.setItem(15, new GuiItem(contractIcon, event -> {
            // Coming soon action
        }));
    }

    private void buildReputationDisplay(Player player) {
        RumorManager rumorManager = plugin.getRumorManager();
        if (rumorManager == null) return;

        BrokerReputation reputationManager = rumorManager.getReputationManager();
        BrokerReputation.PlayerReputation rep = reputationManager.getReputation(player);
        BrokerReputation.ReputationTier tier = reputationManager.getTier(rep.getPoints());

        // Reputation display item (Slot 40 - bottom center)
        ItemStack repIcon = new ItemStack(getTierMaterial(tier));
        repIcon.editMeta(meta -> {
            meta.displayName(MiniMessage.miniMessage().deserialize("<gold><b>Statut Frateur</b></gold>")
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());

            // Tier display
            lore.add(MiniMessage.miniMessage().deserialize("<gray>Rang:</gray> " + tier.getDisplayName())
                    .decoration(TextDecoration.ITALIC, false));

            // Points and progress
            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<gray>Points de faveur: <green>" + rep.getPoints() + "</green></gray>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());

            // Progress bar to next tier
            int currentTierPoints = getTierThreshold(tier);
            int nextTierPoints = getNextTierThreshold(tier);
            if (nextTierPoints > currentTierPoints) {
                int progress = rep.getPoints() - currentTierPoints;
                int needed = nextTierPoints - currentTierPoints;
                int percentage = Math.min(100, (progress * 100) / needed);
                lore.add(MiniMessage.miniMessage().deserialize("<gray>Prochain rang:</gray> " + getProgressBar(percentage))
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(MiniMessage.miniMessage().deserialize("<dark_gray>Progrès: " + progress + "/" + needed + "</dark_gray>")
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(MiniMessage.miniMessage().deserialize("<green><b>Rang maximum atteint !</b></green>")
                        .decoration(TextDecoration.ITALIC, false));
            }

            // Benefits
            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<yellow><b>Bénéfices actuels:</b></yellow>")
                    .decoration(TextDecoration.ITALIC, false));

            double discount = reputationManager.getDiscount(player);
            int stockBonus = reputationManager.getStockBonus(player);

            if (discount > 0) {
                lore.add(MiniMessage.miniMessage().deserialize("<green>  Réduction: -" + discount + "% sur tous les achats</green>")
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(MiniMessage.miniMessage().deserialize("<dark_gray>  Réduction: Aucune (augmentez votre rang!)</dark_gray>")
                        .decoration(TextDecoration.ITALIC, false));
            }

            if (stockBonus > 0) {
                lore.add(MiniMessage.miniMessage().deserialize("<green>  Stock: +" + stockBonus + " items disponibles</green>")
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(MiniMessage.miniMessage().deserialize("<dark_gray>  Stock: Standard (augmentez votre rang!)</dark_gray>")
                        .decoration(TextDecoration.ITALIC, false));
            }

            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<gray>Achetez au Marché Noir pour</gray>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(MiniMessage.miniMessage().deserialize("<gray>gagner des points de faveur.</gray>")
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });
        gui.setItem(40, new GuiItem(repIcon));
    }

    private Material getTierMaterial(BrokerReputation.ReputationTier tier) {
        return switch (tier) {
            case INSIDER -> Material.ENCHANTED_BOOK;
            case VIP -> Material.GOLD_BLOCK;
            case TRUSTED -> Material.GOLD_INGOT;
            case ACQUAINTANCE -> Material.IRON_INGOT;
            default -> Material.COAL;
        };
    }

    private int getTierThreshold(BrokerReputation.ReputationTier tier) {
        return switch (tier) {
            case INSIDER -> 3000;
            case VIP -> 1500;
            case TRUSTED -> 500;
            case ACQUAINTANCE -> 100;
            default -> 0;
        };
    }

    private int getNextTierThreshold(BrokerReputation.ReputationTier tier) {
        return switch (tier) {
            case STRANGER -> 100;
            case ACQUAINTANCE -> 500;
            case TRUSTED -> 1500;
            case VIP -> 3000;
            case INSIDER -> 3000; // Max tier
        };
    }

    private String getProgressBar(int percentage) {
        int bars = 20;
        int filled = (percentage * bars) / 100;
        StringBuilder bar = new StringBuilder("<gray>[</gray>");
        for (int i = 0; i < bars; i++) {
            if (i < filled) {
                bar.append("<green>│</green>");
            } else {
                bar.append("<dark_gray>│</dark_gray>");
            }
        }
        bar.append("<gray>] </gray><green>").append(percentage).append("%</green>");
        return bar.toString();
    }

    private void addNavigation(Player player) {
        // Back to Main Menu (Slot 49 - bottom row, center)
        ItemStack back = new ItemStack(Material.ARROW);
        back.editMeta(m -> m.displayName(
                MiniMessage.miniMessage().deserialize("<white>Retour au menu principal</white>")
                        .decoration(TextDecoration.ITALIC, false)));

        gui.setItem(49, new GuiItem(back, event -> {
             player.closeInventory();
             player.performCommand("tradeflow");
        }));
    }

    public void open(Player player) {
        gui.open(player);
    }
}
