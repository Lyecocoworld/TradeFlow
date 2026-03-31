package com.github.lye.gui;

import com.github.lye.TradeFlow;
import com.github.lye.config.settings.IGuiSettings;
import dev.triumphteam.gui.guis.BaseGui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class BackgroundUtil {

    private BackgroundUtil() {
    }

    public static void fillBackground(BaseGui gui, TradeFlow plugin) {
        IGuiSettings settings = plugin.getGuiSettings();
        List<String> themeColors = settings.getThemeColors();

        if (themeColors != null && !themeColors.isEmpty()) {
            List<GuiItem> items = new ArrayList<>();
            for (String color : themeColors) {
                Material mat = Material.matchMaterial(color);
                if (mat != null) {
                    items.add(new GuiItem(new ItemStack(mat), event -> event.setCancelled(true)));
                }
            }

            if (!items.isEmpty()) {
                if (gui instanceof PaginatedGui) {
                    gui.getFiller().fillBorder(items);
                } else {
                    gui.getFiller().fill(items);
                }
                return;
            }
        }

        String matName = settings.getBackground();
        Material mat = Material.matchMaterial(matName != null ? matName : "");
        if (mat == null) {
            mat = Material.BLACK_STAINED_GLASS_PANE;
        }

        ItemStack pane = new ItemStack(mat);
        GuiItem backgroundItem = new GuiItem(pane, event -> event.setCancelled(true));

        // PaginatedGui ne supporte pas le fill() complet.
        if (gui instanceof PaginatedGui) {
            gui.getFiller().fillBorder(backgroundItem);
        } else {
            gui.getFiller().fill(backgroundItem);
        }
    }
}
