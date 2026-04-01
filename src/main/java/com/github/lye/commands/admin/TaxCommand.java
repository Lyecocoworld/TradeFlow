package com.github.lye.commands.admin;

import com.github.lye.TradeFlow;
import com.github.lye.commands.core.BaseCommand;
import com.github.lye.commands.core.CommandManager;
import com.github.lye.config.settings.ITaxSettings;
import com.github.lye.data.TaxManager;
import com.github.lye.data.TaxRecord;
import com.github.lye.util.Format;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin command for managing the tax system.
 * <p>
 * Usage: /tfadmin taxes [stats|player <name>|reload|reset <player>]</p>
 *
 * @author  lye
 * @since   0.1
 */
public class TaxCommand extends BaseCommand {

    public TaxCommand(TradeFlow plugin) {
        super(plugin, "taxes", "tradeflow.admin", "Manage tax system", "/tfadmin taxes");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "stats" -> showStats(sender);
            case "player" -> handlePlayerCommand(sender, args);
            case "reload" -> handleReload(sender);
            case "reset" -> handleReset(sender, args);
            case "top" -> showTopPayers(sender);
            default -> showHelp(sender);
        }

        return true;
    }

    /**
     * Displays overall tax statistics.
     */
    private void showStats(CommandSender sender) {
        TaxManager taxManager = plugin.getTaxManager();
        ITaxSettings taxSettings = plugin.getTaxSettings();

        if (taxManager == null) {
            sender.sendMessage(Component.text("§cTax system is not available."));
            return;
        }

        sender.sendMessage(Component.text("§6§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(Component.text("§6§l  Système de Taxes - Statistiques"));
        sender.sendMessage(Component.text("§6§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(Component.empty());

        // System status
        boolean enabled = taxSettings != null && taxSettings.isEnabled();
        sender.sendMessage(Component.text("§7Statut: " + (enabled ? "§aActif" : "§cInactif")));

        if (enabled && taxSettings != null) {
            sender.sendMessage(Component.text("§7Taux par défaut: §e" + (taxSettings.getDefaultTaxRate() * 100) + "%"));
            sender.sendMessage(Component.text("§7Taux achat: §e" + (taxSettings.getBuyTaxRate() * 100) + "%"));
            sender.sendMessage(Component.text("§7Taux vente: §e" + (taxSettings.getSellTaxRate() * 100) + "%"));
            sender.sendMessage(Component.text("§7Taux max: §e" + (taxSettings.getMaximumTaxRate() * 100) + "%"));
            sender.sendMessage(Component.text("§7Compte Trésor: §e" + taxSettings.getTreasuryAccount()));
        }

        sender.sendMessage(Component.empty());

        // Collection stats
        double totalCollected = taxManager.getTotalTaxesCollected();
        int recordCount = taxManager.getRecordCount();

        sender.sendMessage(Component.text("§7Total collecté: §a" + Format.currency(totalCollected)));
        sender.sendMessage(Component.text("§7Nombre de transactions taxées: §e" + recordCount));

        // Today's taxes
        long todayStart = ZonedDateTime.now().withHour(0).withMinute(0).withSecond(0).toInstant().toEpochMilli();
        double todayTaxes = taxManager.getTaxesCollectedInPeriod(todayStart, System.currentTimeMillis());
        sender.sendMessage(Component.text("§7Aujourd'hui: §a" + Format.currency(todayTaxes)));

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("§7Utilisez §e/tfadmin taxes top §7pour voir les plus gros contributeurs."));
        sender.sendMessage(Component.text("§6§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    /**
     * Handles player-specific tax commands.
     */
    private void handlePlayerCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("§cUsage: /tfadmin taxes player <joueur>"));
            return;
        }

        TaxManager taxManager = plugin.getTaxManager();
        if (taxManager == null) {
            sender.sendMessage(Component.text("§cTax system is not available."));
            return;
        }

        String playerName = args[1];
        UUID playerUuid = findPlayerUuid(playerName);

        if (playerUuid == null) {
            sender.sendMessage(Component.text("§cJoueur introuvable: " + playerName));
            return;
        }

        // Get player tax info
        List<TaxRecord> records = taxManager.getPlayerTaxRecords(playerUuid);
        double playerTotal = records.stream().mapToDouble(TaxRecord::getTaxAmount).sum();
        double playerVolume = taxManager.getPlayerVolume(playerUuid);

        sender.sendMessage(Component.text("§6§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(Component.text("§6§l  Taxes: " + playerName));
        sender.sendMessage(Component.text("§6§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("§7Volume de transactions: §e" + Format.currency(playerVolume)));
        sender.sendMessage(Component.text("§7Total des taxes payées: §a" + Format.currency(playerTotal)));
        sender.sendMessage(Component.text("§7Nombre de transactions taxées: §e" + records.size()));

        // Show recent tax records
        if (!records.isEmpty()) {
            sender.sendMessage(Component.empty());
            sender.sendMessage(Component.text("§7Dernières transactions taxées:"));

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm");
            records.stream()
                .sorted((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()))
                .limit(5)
                .forEach(record -> {
                    ZonedDateTime dateTime = ZonedDateTime.ofInstant(
                        Instant.ofEpochMilli(record.getTimestamp()),
                        ZoneId.systemDefault()
                    );
                    sender.sendMessage(Component.text("§8  §7[" + formatter.format(dateTime) + "] §e" +
                        Format.currency(record.getTaxAmount()) + " §7(" +
                        (int)(record.getTaxRate() * 100) + "%) §8- §7" + record.getShopName()));
                });
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("§6§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    /**
     * Handles tax reset command.
     */
    private void handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tradeflow.admin.tax.reset")) {
            sender.sendMessage(Component.text("§cVous n'avez pas la permission de réinitialiser les taxes."));
            return;
        }

        TaxManager taxManager = plugin.getTaxManager();
        if (taxManager == null) {
            sender.sendMessage(Component.text("§cTax system is not available."));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("§cUsage: /tfadmin taxes reset <joueur|all>"));
            return;
        }

        String target = args[1].toLowerCase();

        if (target.equals("all")) {
            taxManager.resetAllVolumes();
            sender.sendMessage(Component.text("§aTous les volumes de taxes ont été réinitialisés."));
        } else {
            UUID playerUuid = findPlayerUuid(target);
            if (playerUuid == null) {
                sender.sendMessage(Component.text("§cJoueur introuvable: " + target));
                return;
            }
            taxManager.resetPlayerVolume(playerUuid);
            sender.sendMessage(Component.text("§aVolume de taxes réinitialisé pour: " + target));
        }
    }

    /**
     * Handles tax reload command.
     */
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("tradeflow.admin.tax.reload")) {
            sender.sendMessage(Component.text("§cVous n'avez pas la permission de recharger les taxes."));
            return;
        }

        boolean success = plugin.getBootstrap().getConfigLoader().reload();
        if (success) {
            sender.sendMessage(Component.text("§aConfiguration des taxes rechargée."));
        } else {
            sender.sendMessage(Component.text("§cErreur lors du rechargement de la configuration."));
        }
    }

    /**
     * Shows top tax payers.
     */
    private void showTopPayers(CommandSender sender) {
        TaxManager taxManager = plugin.getTaxManager();
        if (taxManager == null) {
            sender.sendMessage(Component.text("§cTax system is not available."));
            return;
        }

        sender.sendMessage(Component.text("§6§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(Component.text("§6§l  Top Contributeurs du Trésor"));
        sender.sendMessage(Component.text("§6§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(Component.empty());

        // Get all tax records grouped by player
        Map<UUID, Double> playerTotals = taxManager.getTaxRecords().values().stream()
            .collect(Collectors.groupingBy(
                TaxRecord::getPlayerUuid,
                Collectors.summingDouble(TaxRecord::getTaxAmount)
            ));

        // Sort and get top 10
        playerTotals.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .limit(10)
            .forEach((entry) -> {
                UUID uuid = entry.getKey();
                double total = entry.getValue();
                String name = getPlayerName(uuid);
                sender.sendMessage(Component.text("§7  §e" + name + "§7: §a" + Format.currency(total)));
            });

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("§6§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(Component.text("§6§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(Component.text("§6§l  Commandes de Taxes"));
        sender.sendMessage(Component.text("§6§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(Component.text("§e/tfadmin taxes stats§7 - Affiche les statistiques globales"));
        sender.sendMessage(Component.text("§e/tfadmin taxes player <joueur>§7 - Voir les taxes d'un joueur"));
        sender.sendMessage(Component.text("§e/tfadmin taxes top§7 - Top des contributeurs"));
        sender.sendMessage(Component.text("§e/tfadmin taxes reset <joueur|all>§7 - Réinitialise les volumes"));
        sender.sendMessage(Component.text("§e/tfadmin taxes reload§7 - Recharge la configuration"));
        sender.sendMessage(Component.text("§6§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    /**
     * Finds a player UUID by name.
     */
    private UUID findPlayerUuid(String name) {
        // Try online player first
        Player player = plugin.getServer().getPlayer(name);
        if (player != null) {
            return player.getUniqueId();
        }

        // Try offline player
        var offlinePlayer = plugin.getServer().getOfflinePlayer(name);
        if (offlinePlayer != null && offlinePlayer.getUniqueId() != null) {
            return offlinePlayer.getUniqueId();
        }

        // Try searching through tax records
        for (TaxRecord record : plugin.getTaxManager().getTaxRecords().values()) {
            if (record.getPlayerName().equalsIgnoreCase(name)) {
                return record.getPlayerUuid();
            }
        }

        return null;
    }

    /**
     * Gets a player's name by UUID.
     */
    private String getPlayerName(UUID uuid) {
        if (uuid == null) return "Inconnu";

        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null) {
            return player.getName();
        }

        var offlinePlayer = plugin.getServer().getOfflinePlayer(uuid);
        if (offlinePlayer != null && offlinePlayer.getName() != null) {
            return offlinePlayer.getName();
        }

        // Try to find name from tax records
        for (TaxRecord record : plugin.getTaxManager().getTaxRecords().values()) {
            if (record.getPlayerUuid().equals(uuid)) {
                return record.getPlayerName();
            }
        }

        return uuid.toString().substring(0, 8);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return switch (args.length) {
            case 1 -> List.of("stats", "player", "top", "reset", "reload");
            case 2 -> {
                if (args[0].equalsIgnoreCase("reset")) {
                    yield List.of("all");
                }
                // Suggest player names
                yield plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .sorted()
                    .toList();
            }
            default -> List.of();
        };
    }
}
