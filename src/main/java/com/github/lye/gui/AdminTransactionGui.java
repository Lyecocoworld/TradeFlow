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
 * Admin GUI for transaction tracking and monitoring.
 * <p>
 * Displays recent transactions with filtering capabilities,
 * suspicious activity alerts, and admin actions.</p>
 *
 * @author  lye
 * @since   0.1
 */
public class AdminTransactionGui {

    private final TradeFlow plugin;
    private final AdminNavigator navigator;
    private final Player admin;
    private final Gui gui;
    private final TransactionFilter filter;

    private static final int ITEMS_PER_PAGE = 45;

    public AdminTransactionGui(TradeFlow plugin, AdminNavigator navigator, Player admin) {
        this(plugin, navigator, admin, new TransactionFilter());
    }

    public AdminTransactionGui(TradeFlow plugin, AdminNavigator navigator, Player admin, TransactionFilter filter) {
        this.plugin = plugin;
        this.navigator = navigator;
        this.admin = admin;
        this.filter = filter;

        this.gui = Gui.gui()
                .rows(6)
                .title(GuiTextCache.title("Journal des Transactions"))
                .disableAllInteractions()
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));
        BackgroundUtil.fillBackground(gui, plugin);

        buildContent();
    }

    private void buildContent() {
        List<Transaction> transactions = getFilteredTransactions();
        List<Transaction> suspicious = transactions.stream()
                .filter(this::isSuspicious)
                .toList();

        final int fTotalTx = transactions.size();
        final double fTotalVolume = transactions.stream()
                .mapToDouble(t -> t.getPrice() * t.getAmount())
                .sum();
        final long fSuspiciousCount = suspicious.size();

        // --- Row 1: Stats ---
        ItemStack stats = new ItemStack(Material.BOOK);
        stats.editMeta(meta -> {
            meta.displayName(MiniMessage.miniMessage().deserialize("<gold>Statistiques</gold>")
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<white>Total transactions: <yellow>" + fTotalTx + "</yellow></white>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(MiniMessage.miniMessage().deserialize("<white>Volume total: <yellow>" + Format.currency(fTotalVolume) + "</yellow></white>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            if (fSuspiciousCount > 0) {
                lore.add(MiniMessage.miniMessage().deserialize("<red>Suspicieuses: <yellow>" + fSuspiciousCount + "</yellow></red>")
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(MiniMessage.miniMessage().deserialize("<green>Aucune transaction suspecte</green>")
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        });
        gui.setItem(4, new GuiItem(stats));

        // --- Row 2-5: Transactions list ---
        populateTransactionList(transactions);

        // --- Row 6: Navigation Bar ---
        NavigationBar.apply(gui, new NavigationBar.Config(6)
                .title("Transactions")
                .onBack(() -> navigator.openMainMenu(admin))
                .showClose(true));

        // Filter button (overwrites slot 50 if needed)
        ItemStack filterBtn = new ItemStack(Material.COMPASS);
        filterBtn.editMeta(m -> {
            m.displayName(MiniMessage.miniMessage().deserialize("<gold>Filtres</gold>")
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<white>Type: " + getFilterDisplay(filter.type) + "</white>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(MiniMessage.miniMessage().deserialize("<white>Joueur: <yellow>" + (filter.playerName != null ? filter.playerName : "Tous") + "</yellow></white>")
                    .decoration(TextDecoration.ITALIC, false));
        });
        gui.setItem(50, new GuiItem(filterBtn)); // Place next to close button
    }

    private void populateTransactionList(List<Transaction> transactions) {
        int slot = 9;
        int displayCount = Math.min(transactions.size(), ITEMS_PER_PAGE);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm");

        for (int i = 0; i < displayCount; i++) {
            Transaction tx = transactions.get(i);
            ItemStack item = createTransactionItem(tx, formatter);
            gui.setItem(slot++, new GuiItem(item));

            // Skip border slots
            if (slot == 18 || slot == 27 || slot == 36) {
                slot += 1;
            }
        }
    }

    private ItemStack createTransactionItem(Transaction tx, DateTimeFormatter formatter) {
        Material material = tx.getPosition() == Transaction.TransactionType.BUY
                ? Material.DIAMOND : Material.GOLD_INGOT;

        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            String playerName = getPlayerName(tx.getPlayer());
            String typeColor = tx.getPosition() == Transaction.TransactionType.BUY ? "<green>" : "<red>";
            double totalValue = tx.getPrice() * tx.getAmount();

            meta.displayName(MiniMessage.miniMessage().deserialize(
                            typeColor + tx.getPosition().name() + "</typeColor> <white>x" + tx.getAmount() + "</white>")
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<white>Joueur: <yellow>" + playerName + "</yellow></white>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(MiniMessage.miniMessage().deserialize("<white>Prix: <yellow>" + Format.currency(tx.getPrice()) + "</yellow></white>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(MiniMessage.miniMessage().deserialize("<white>Shop: <yellow>" + tx.getItem() + "</yellow></white>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(MiniMessage.miniMessage().deserialize("<white>Total: <yellow>" + Format.currency(totalValue) + "</yellow></white>")
                    .decoration(TextDecoration.ITALIC, false));

            if (tx.getTimestamp() > 0) {
                ZonedDateTime dateTime = ZonedDateTime.ofInstant(
                        Instant.ofEpochMilli(tx.getTimestamp()),
                        ZoneId.systemDefault()
                );
                lore.add(MiniMessage.miniMessage().deserialize("<white>Date: <yellow>" + formatter.format(dateTime) + "</yellow></white>")
                        .decoration(TextDecoration.ITALIC, false));
            }

            if (isSuspicious(tx)) {
                lore.add(Component.empty());
                lore.add(MiniMessage.miniMessage().deserialize("<red>⚠ Transaction suspecte</red>")
                        .decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);
        });

        return item;
    }

    private List<Transaction> getFilteredTransactions() {
        Map<String, Transaction> allTx = plugin.getLoadedTransactions();
        if (allTx == null) {
            return Collections.emptyList();
        }

        return allTx.values().stream()
                .filter(tx -> {
                    if (filter.type != null && tx.getPosition() != filter.type) {
                        return false;
                    }
                    if (filter.playerUuid != null && !filter.playerUuid.equals(tx.getPlayer())) {
                        return false;
                    }
                    if (filter.shopName != null && !filter.shopName.equals(tx.getItem())) {
                        return false;
                    }
                    if (filter.minAmount > 0 && tx.getAmount() < filter.minAmount) {
                        return false;
                    }
                    return true;
                })
                .sorted((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()))
                .limit(ITEMS_PER_PAGE)
                .toList();
    }

    private boolean isSuspicious(Transaction tx) {
        double totalValue = tx.getPrice() * tx.getAmount();
        return totalValue > 100000;
    }

    private String getPlayerName(UUID uuid) {
        if (uuid == null) return "Inconnu";
        org.bukkit.OfflinePlayer offline = plugin.getServer().getOfflinePlayer(uuid);
        return offline != null ? offline.getName() : uuid.toString().substring(0, 8);
    }

    private String getFilterDisplay(Transaction.TransactionType type) {
        if (type == null) return "<yellow>Tous</yellow>";
        return type == Transaction.TransactionType.BUY ? "<green>Achats</green>" : "<red>Ventes</red>";
    }

    public void open(Player admin) {
        gui.open(admin);
    }

    public static class TransactionFilter {
        public Transaction.TransactionType type;
        public UUID playerUuid;
        public String playerName;
        public String shopName;
        public int minAmount;
        public long timestampFrom;

        public TransactionFilter() {
            this.minAmount = 0;
            this.timestampFrom = System.currentTimeMillis() - 86400000;
        }
    }
}
