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

public class LicenseConfirmGui {

    private final TradeFlow plugin;
    private final Gui gui;

    public LicenseConfirmGui(TradeFlow plugin, Player player, PlayerLicense current, License newLicense) {
        this.plugin = plugin;

        this.gui = Gui.gui()
                .rows(3)
                .title(GuiTextCache.title(GuiTextCache.themed("<gold><b>Confirmation requise</b></gold>")))
                .disableAllInteractions()
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));
        BackgroundUtil.fillBackground(gui, plugin);

        buildContent(player, current, newLicense);
    }

    private void buildContent(Player player, PlayerLicense current, License newLicense) {
        License currentDef = plugin.getLicenseManager().getLicenseDefinition(current.getLicenseId());
        String currentName = currentDef != null ? currentDef.getName() : "Inconnue";

        // Info Item (Slot 13)
        ItemStack info = new ItemStack(Material.BARRIER); // Used Barrier for Warning
        if (Material.getMaterial("WARNING_ICON") == null) {
            info = new ItemStack(Material.RED_STAINED_GLASS_PANE); // Fallback
            if (Material.getMaterial("TNT") != null) info = new ItemStack(Material.TNT);
        }

        info.editMeta(meta -> {
            meta.displayName(GuiTextCache.themedComponent("<red><b>ATTENTION</b></red>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(GuiTextCache.themedComponent("<gray>Licence actuelle: <yellow>" + currentName + "</yellow></gray>"));
            lore.add(GuiTextCache.themedComponent("<gray>Acheter <yellow>" + newLicense.getName() + "</yellow> va</gray>"));
            lore.add(GuiTextCache.themedComponent("<red><bold>ECRASER DEFINITIVEMENT</bold></red> <gray>l'ancienne.</gray>"));
            lore.add(Component.empty());
            lore.add(GuiTextCache.themedComponent("<red>Aucun remboursement possible.</red>"));
            meta.lore(lore);
        });
        gui.setItem(13, new GuiItem(info));

        // Confirm (Slot 15)
        ItemStack confirm = new ItemStack(Material.LIME_WOOL);
        confirm.editMeta(meta -> meta.displayName(GuiTextCache.themedComponent("<gold><b>Confirmer l'echange</b></gold>")
                .decoration(TextDecoration.ITALIC, false)));
        gui.setItem(15, new GuiItem(confirm, event -> {
            plugin.getLicenseManager().purchaseLicense(player, newLicense.getId());
            player.closeInventory();
        }));

        // Cancel (Slot 11)
        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        cancel.editMeta(meta -> meta.displayName(GuiTextCache.themedComponent("<red><b>ANNULER</b></red>")
                .decoration(TextDecoration.ITALIC, false)));
        gui.setItem(11, new GuiItem(cancel, event -> new LicenseGui(plugin, player).open(player)));
    }

    public void open(Player player) {
        gui.open(player);
    }
}
