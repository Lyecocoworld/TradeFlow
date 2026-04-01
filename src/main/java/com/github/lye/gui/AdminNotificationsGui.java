package com.github.lye.gui;

import com.github.lye.TradeFlow;
import com.github.lye.data.Transaction;
import com.github.lye.util.Format;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Admin GUI for notifications and alerts.
 * <p>
 * Displays suspicious transactions, system alerts, and important events.
 * Allows marking notifications as read and dismissing alerts.</p>
 *
 * @author  lye
 * @since   0.1
 */
public class AdminNotificationsGui {

    private final TradeFlow plugin;
    private final AdminNavigator navigator;
    private final Player admin;
    private final Gui gui;

    public AdminNotificationsGui(TradeFlow plugin, AdminNavigator navigator, Player admin) {
        this.plugin = plugin;
        this.navigator = navigator;
        this.admin = admin;

        this.gui = Gui.gui()
                .rows(6)
                .title(GuiTextCache.title("Notifications Admin"))
                .disableAllInteractions()
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));
        BackgroundUtil.fillBackground(gui, plugin);

        buildContent();
    }

    private void buildContent() {
        List<Transaction> suspicious = getSuspiciousTransactions();
        List<SystemAlert> alerts = getSystemAlerts();

        // --- Row 1: Summary ---
        ItemStack summary = new ItemStack(Material.BELL);
        summary.editMeta(meta -> {
            meta.displayName(MiniMessage.miniMessage().deserialize("<gold>Notifications</gold>")
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<white>Transactions suspectes: <yellow>" + suspicious.size() + "</yellow></white>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(MiniMessage.miniMessage().deserialize("<white>Alertes système: <yellow>" + alerts.size() + "</yellow></white>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            if (suspicious.isEmpty() && alerts.isEmpty()) {
                lore.add(MiniMessage.miniMessage().deserialize("<green>Aucune notification</green>")
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        });
        gui.setItem(4, new GuiItem(summary));

        // --- Row 2-5: Alerts and Transactions ---
        int slot = 9;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm");

        // Display system alerts first
        for (SystemAlert alert : alerts) {
            ItemStack alertItem = new ItemStack(getMaterialForAlert(alert));
            alertItem.editMeta(meta -> {
                meta.displayName(MiniMessage.miniMessage().deserialize("<gold>" + alert.getIcon() + " " + alert.getTitle() + "</gold>")
                        .decoration(TextDecoration.ITALIC, false));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(MiniMessage.miniMessage().deserialize("<white>" + alert.getDescription() + "</white>")
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
            });
            gui.setItem(slot++, new GuiItem(alertItem));

            if (slot == 18 || slot == 27 || slot == 36) {
                slot += 1;
            }
        }

        // Display suspicious transactions (up to 20)
        int txCount = Math.min(20, suspicious.size());
        for (int i = 0; i < txCount && slot < 45; i++) {
            Transaction tx = suspicious.get(i);
            ItemStack txItem = createSuspiciousTransactionItem(tx, formatter);
            gui.setItem(slot++, new GuiItem(txItem));

            if (slot == 18 || slot == 27 || slot == 36) {
                slot += 1;
            }
        }

        if (suspicious.isEmpty() && alerts.isEmpty()) {
            ItemStack noAlerts = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
            noAlerts.editMeta(meta -> {
                meta.displayName(MiniMessage.miniMessage().deserialize("<green>Aucune notification</green>")
                        .decoration(TextDecoration.ITALIC, false));
            });
            gui.setItem(22, new GuiItem(noAlerts));
        }

        // --- Row 6: Navigation Bar ---
        NavigationBar.apply(gui, new NavigationBar.Config(6)
                .title("Notifications")
                .onBack(() -> navigator.openMainMenu(admin))
                .showClose(true));

        // View all transactions button (overwrites slot 50)
        ItemStack viewAll = new ItemStack(Material.BOOKSHELF);
        viewAll.editMeta(m -> {
            m.displayName(MiniMessage.miniMessage().deserialize("<gold>Voir Toutes</gold>")
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<white>Ouvrir le journal complet</white>")
                    .decoration(TextDecoration.ITALIC, false));
        });
        gui.setItem(50, new GuiItem(viewAll, event -> navigator.openTransactions(admin)));
    }

    private ItemStack createSuspiciousTransactionItem(Transaction tx, DateTimeFormatter formatter) {
        ItemStack item = new ItemStack(Material.DIAMOND);
        item.editMeta(meta -> {
            String playerName = getPlayerName(tx.getPlayer());
            double totalValue = tx.getPrice() * tx.getAmount();

            meta.displayName(MiniMessage.miniMessage().deserialize(
                            "<red>⚠ x" + tx.getAmount() + " " + tx.getItem() + "</red>")
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<white>Joueur: <yellow>" + playerName + "</yellow></white>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(MiniMessage.miniMessage().deserialize("<white>Valeur: <yellow>" + Format.currency(totalValue) + "</yellow></white>")
                    .decoration(TextDecoration.ITALIC, false));

            if (tx.getTimestamp() > 0) {
                ZonedDateTime dateTime = ZonedDateTime.ofInstant(
                        Instant.ofEpochMilli(tx.getTimestamp()),
                        ZoneId.systemDefault()
                );
                lore.add(MiniMessage.miniMessage().deserialize("<white>Date: <yellow>" + formatter.format(dateTime) + "</yellow></white>")
                        .decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);
        });

        return item;
    }

    private Material getMaterialForAlert(SystemAlert alert) {
        return switch (alert.getSeverity()) {
            case "high" -> Material.REDSTONE_BLOCK;
            case "medium" -> Material.GOLD_BLOCK;
            default -> Material.YELLOW_WOOL;
        };
    }

    private List<Transaction> getSuspiciousTransactions() {
        Map<String, Transaction> txMap = plugin.getLoadedTransactions();
        if (txMap == null) {
            return Collections.emptyList();
        }

        return txMap.values().stream()
                .filter(tx -> {
                    double totalValue = tx.getPrice() * tx.getAmount();
                    return totalValue > 100000;
                })
                .sorted((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()))
                .toList();
    }

    private List<SystemAlert> getSystemAlerts() {
        List<SystemAlert> alerts = new ArrayList<>();

        // Check economic events
        com.github.lye.events.EconomicEventManager evtMgr = plugin.getEconomicEventManager();
        if (evtMgr != null && evtMgr.getActiveEvent() != null) {
            alerts.add(new SystemAlert(
                    "Événement économique en cours",
                    "Un événement affecte les prix actuellement",
                    "medium",
                    "⚡"
            ));
        }

        // Check central bank balance
        double bankBalance = com.github.lye.util.EconomyUtil.getCentralBankBalance(plugin);
        if (bankBalance < 100000) {
            alerts.add(new SystemAlert(
                    "Banque Centrale faible",
                    "Réserve: " + Format.currency(bankBalance),
                    "high",
                    "⚠"
            ));
        }

        return alerts;
    }

    private String getPlayerName(UUID uuid) {
        if (uuid == null) return "Inconnu";
        org.bukkit.OfflinePlayer offline = plugin.getServer().getOfflinePlayer(uuid);
        return offline != null ? offline.getName() : uuid.toString().substring(0, 8);
    }

    public void open(Player admin) {
        gui.open(admin);
    }

    private static class SystemAlert {
        private final String title;
        private final String description;
        private final String severity;
        private final String icon;

        public SystemAlert(String title, String description, String severity, String icon) {
            this.title = title;
            this.description = description;
            this.severity = severity;
            this.icon = icon;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public String getSeverity() {
            return severity;
        }

        public String getIcon() {
            return icon;
        }
    }
}
