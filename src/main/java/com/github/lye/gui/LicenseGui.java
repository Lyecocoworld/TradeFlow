package com.github.lye.gui;

import com.github.lye.TradeFlow;
import com.github.lye.gui.framework.TriumphGuiAdapter;
import com.github.lye.license.License;
import com.github.lye.license.LicenseManager;
import com.github.lye.license.PlayerLicense;
import com.github.lye.service.IMessageService;
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

public class LicenseGui {

    private final TradeFlow plugin;
    private final LicenseManager licenseManager;
    private final Gui gui;

    public LicenseGui(TradeFlow plugin, Player player) {
        this.plugin = plugin;
        this.licenseManager = plugin.getServices().get(LicenseManager.class);

        this.gui = Gui.gui()
                .rows(3)
                .title(GuiTextCache.title(GuiTextCache.themed("<gold><b>Licences commerciales</b></gold>")))
                .disableAllInteractions()
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));
        BackgroundUtil.fillBackground(gui, plugin);

        buildContent(player);
    }

    private void buildContent(Player player) {
        List<License> licenses = licenseManager.getAllDefinitions();
        PlayerLicense active = licenseManager.getActiveLicense(player);

        int[] slots = {11, 12, 13, 14, 15};
        int index = 0;

        for (License license : licenses) {
            if (index >= slots.length) {
                break;
            }

            boolean isActive = active != null && active.getLicenseId().equals(license.getId());

            ItemStack item = new ItemStack(isActive ? Material.ENCHANTED_BOOK : Material.PAPER);
            item.editMeta(meta -> {
                meta.displayName(GuiTextCache.themedComponent("<gold><b>" + license.getName() + "</b></gold>"));

                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                for (String line : license.getLore()) {
                    lore.add(GuiTextCache.themedComponent("<gray>" + line + "</gray>"));
                }
                lore.add(Component.empty());
                lore.add(GuiTextCache.themedComponent("<gray>Duree: <yellow>" + license.getDurationDays() + " jours</yellow>"));
                lore.add(GuiTextCache.themedComponent("<gray>Prix: <yellow>" + Format.currency(license.getPrice()) + "</yellow>"));
                lore.add(Component.empty());

                if (isActive) {
                    lore.add(GuiTextCache.themedComponent("<green>Active</green>"));
                } else {
                    lore.add(GuiTextCache.themedComponent("<yellow>Clic pour acheter</yellow>"));
                }
                meta.lore(lore);
            });

            gui.setItem(slots[index], new GuiItem(item, event -> {
                if (isActive) {
                    plugin.getServices().get(IMessageService.class).sendInfoMessage(player, "<green>Vous possedez deja cette licence.</green>", null);
                    return;
                }

                if (active != null) {
                    new LicenseConfirmGui(plugin, player, active, license).open(player);
                } else {
                    licenseManager.purchaseLicense(player, license.getId());
                    new LicenseGui(plugin, player).open(player);
                }
            }));

            index++;
        }

        // Navigation bar at bottom (row 3)
        NavigationBar.apply(gui, new NavigationBar.Config(3)
                // Removed title to hide middle button
                .onBack(() -> {
                    // Back logic: Return to Utility Menu or Main Menu
                    // Since licenses are usually accessed via Utility -> License
                    new UtilityGui(plugin, player).open(player);
                })
        );
    }

    public void open(Player player) {
        TriumphGuiAdapter.openSafe(gui, player, plugin);
    }
}
