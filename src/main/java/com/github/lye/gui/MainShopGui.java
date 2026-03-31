package com.github.lye.gui;

import com.github.lye.TradeFlow;
import com.github.lye.data.Section;
import com.github.lye.data.ShopUtil;
import com.github.lye.gui.state.PlayerShopState;
import com.github.lye.util.Format;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.github.lye.util.TradeFlowLogger;

/**
 * Main shop GUI - The central hub for all player-facing features.
 * <p>
 * Layout (5 rows):
 * - Row 1: Title bar with utility button (slot 8)
 * - Row 2-4: Pyramid layout for shop sections (7-3-1)
 * - Row 5: Help button (bottom right, slot 44)</p>
 *
 * @author  lye
 * @since   0.1
 */
public class MainShopGui {

    private final TradeFlow plugin;
    private final GuiNavigator navigator;
    private final PlayerShopState state;
    private final Gui gui;
    private final TradeFlowLogger logger;

    // Quick access button slots (bottom row)
    private static final int HELP_BUTTON_SLOT = 44; // Bottom right corner

    public MainShopGui(TradeFlow plugin, GuiNavigator navigator, PlayerShopState state, TradeFlowLogger logger) {
        this.plugin = plugin;
        this.navigator = navigator;
        this.state = state;
        this.logger = logger;

        String marketDisplayName = plugin.getGuiSettings() != null
                ? plugin.getGuiSettings().getMarketDisplayName()
                : "TradeFlow";

        this.gui = Gui.gui()
                .rows(5) // 5 rows: title, pyramid (3 rows), help + navigation
                .title(GuiTextCache.title(marketDisplayName))
                .disableAllInteractions()
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));

        // Fill background
        BackgroundUtil.fillBackground(gui, plugin);

        // --- Pyramid Layout (7 - 3 - 1) for shop sections ---
        // Row 2: 7 items (Slots 10-16)
        // Row 3: 3 items (Slots 21-23) -> Centered on 22
        // Row 4: 1 item  (Slot 31)    -> Centered on 31
        int[] sectionSlots = {
                10, 11, 12, 13, 14, 15, 16,
                21, 22, 23,
                31
        };

        // Clear section slots
        for (int slot : sectionSlots) {
            gui.setItem(slot, new GuiItem(new ItemStack(Material.AIR)));
        }

        // --- Utility Hub Button (Slot 8) ---
        ItemStack utilityIcon = new ItemStack(Material.CHEST);
        utilityIcon.editMeta(meta -> {
            // Use standardized gold/bold title from messages.yml
            meta.displayName(Format.getComponent("main-shop-utility-title")
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(java.util.List.of(
                    Component.empty(),
                    Format.getComponent("main-shop-utility-lore")
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Format.getComponent("main-shop-click-to-open")
                            .decoration(TextDecoration.ITALIC, false)
            ));
        });
        gui.setItem(8, new GuiItem(utilityIcon, event -> {
            Player player = (Player) event.getWhoClicked();
            new UtilityGui(plugin, player).open(player);
        }));

        // Build shop sections
        buildSections(plugin.getShopUtil(), sectionSlots);

        // --- Bottom Row: Quick Access Buttons ---
        buildQuickAccessButtons();
    }

    /**
     * Builds the quick access buttons in the bottom row.
     */
    private void buildQuickAccessButtons() {
        // Help & Tutorial Button
        ItemStack helpIcon = new ItemStack(Material.KNOWLEDGE_BOOK);
        helpIcon.editMeta(meta -> {
            meta.displayName(Format.getComponent("main-shop-help-title")
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(java.util.List.of(
                    Component.empty(),
                    Format.getComponent("main-shop-help-lore")
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Format.getComponent("main-shop-click-to-open")
                            .decoration(TextDecoration.ITALIC, false)
            ));
        });
        gui.setItem(HELP_BUTTON_SLOT, new GuiItem(helpIcon, event -> {
            Player player = (Player) event.getWhoClicked();
            new HelpGui(plugin, player).open(player);
        }));
    }

    private void buildSections(ShopUtil shopUtil, int[] gridSlots) {
        String[] names = shopUtil.getSectionNames();

        int index = 0;

        for (String sectionName : names) {
            if (index >= gridSlots.length) break;

            Section section = shopUtil.getSection(sectionName);
            if (section == null) {
                continue;
            }

            int slot = gridSlots[index];
            ItemStack icon = section.getItem().clone();

            // Force uniform themed/bold title for section buttons
            icon.editMeta(meta -> {
                String baseName = sectionName;
                if (meta.hasDisplayName()) {
                    baseName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
                }
                meta.displayName(GuiTextCache.themedComponent("<gold><b>" + baseName + "</b></gold>"));
            });

            GuiItem guiItem = new GuiItem(icon, event -> {
                Player player = (Player) event.getWhoClicked();
                logger.info("[GUI] MainShop click: player=" + player.getName()
                        + ", section=" + sectionName + ", slot=" + slot);
                navigator.openSection(player, sectionName);
            });

            // Add trend indicator
            if (plugin.getMarketTrendManager() != null) {
                double weekly = plugin.getMarketTrendManager().getWeeklyTrend(sectionName);
                double monthly = plugin.getMarketTrendManager().getMonthlyTrend();
                double totalTrend = weekly * monthly;

                double percent = (totalTrend - 1.0) * 100.0;
                String arrow = percent >= 0 ? "⬆" : "⬇";
                String color = percent >= 0 ? "<green>" : "<red>";

                icon.editMeta(meta -> {
                    java.util.List<Component> lore = meta.hasLore() ? meta.lore() : new java.util.ArrayList<>();
                    lore.add(Component.empty());
                    lore.add(Format.getComponent("main-shop-trend",
                            Placeholder.parsed("color", color),
                            Placeholder.parsed("arrow", arrow),
                            Placeholder.parsed("percent", String.format("%.1f", percent))
                    ).decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.empty());
                    lore.add(Format.getComponent("main-shop-click-to-open")
                            .decoration(TextDecoration.ITALIC, false));
                    meta.lore(lore);
                });
            }

            gui.setItem(slot, guiItem);
            index++;
        }
    }

    public void open(Player player) {
        gui.open(player);
    }
}
