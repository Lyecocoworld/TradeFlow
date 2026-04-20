package com.github.lye.commands.admin;

import com.github.lye.TradeFlow;
import com.github.lye.commands.core.BaseCommand;
import com.github.lye.commands.core.SubCommand;
import com.github.lye.config.settings.ITaxSettings;
import com.github.lye.data.TaxManager;
import com.github.lye.data.TaxRecord;
import com.github.lye.util.Format;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class TaxCommand extends BaseCommand {

    public TaxCommand(TradeFlow plugin) {
        super(plugin, "taxes", "tradeflow.admin", "Manage tax system", "/tfadmin taxes");
        registerSubCommand(new TaxStatsSub());
        registerSubCommand(new TaxPlayerSub());
        registerSubCommand(new TaxTopSub());
        registerSubCommand(new TaxResetSub());
        registerSubCommand(new TaxReloadSub());
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        if (!super.execute(sender, args)) {
            showHelp(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            return super.onTabComplete(sender, args);
        }
        for (var sub : subCommands) {
            if (sub.getName().equalsIgnoreCase(args[0])) {
                return sub.onTabComplete(sender, tail(args));
            }
        }
        return List.of();
    }

    private void showHelp(CommandSender sender) {
        Format.sendRawMessage(sender, "<gold><strikethrough>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Format.sendRawMessage(sender, "<gold><bold>  Commandes de Taxes");
        Format.sendRawMessage(sender, "<gold><strikethrough>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Format.sendRawMessage(sender, "<yellow>/tfadmin taxes stats<gray> - Affiche les statistiques globales");
        Format.sendRawMessage(sender, "<yellow>/tfadmin taxes player <joueur><gray> - Voir les taxes d'un joueur");
        Format.sendRawMessage(sender, "<yellow>/tfadmin taxes top<gray> - Top des contributeurs");
        Format.sendRawMessage(sender, "<yellow>/tfadmin taxes reset <joueur|all><gray> - Réinitialise les volumes");
        Format.sendRawMessage(sender, "<yellow>/tfadmin taxes reload<gray> - Recharge la configuration");
        Format.sendRawMessage(sender, "<gold><strikethrough>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    static UUID findPlayerUuid(TradeFlow plugin, String name) {
        Player player = plugin.getServer().getPlayer(name);
        if (player != null) return player.getUniqueId();

        var offlinePlayer = plugin.getServer().getOfflinePlayer(name);
        if (offlinePlayer != null && offlinePlayer.getUniqueId() != null) return offlinePlayer.getUniqueId();

        TaxManager tm = plugin.getServices().get(TaxManager.class);
        if (tm != null) {
            for (TaxRecord record : tm.getTaxRecords().values()) {
                if (record.getPlayerName().equalsIgnoreCase(name)) return record.getPlayerUuid();
            }
        }
        return null;
    }

    static String getPlayerName(TradeFlow plugin, UUID uuid) {
        if (uuid == null) return "Inconnu";
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null) return player.getName();
        var offlinePlayer = plugin.getServer().getOfflinePlayer(uuid);
        if (offlinePlayer != null && offlinePlayer.getName() != null) return offlinePlayer.getName();
        TaxManager tm = plugin.getServices().get(TaxManager.class);
        if (tm != null) {
            for (TaxRecord record : tm.getTaxRecords().values()) {
                if (record.getPlayerUuid().equals(uuid)) return record.getPlayerName();
            }
        }
        return uuid.toString().substring(0, 8);
    }

    private static String[] tail(String[] args) {
        if (args.length <= 1) return new String[0];
        String[] out = new String[args.length - 1];
        System.arraycopy(args, 1, out, 0, out.length);
        return out;
    }

    // ==================== SUB-COMMANDS ====================

    private class TaxStatsSub extends SubCommand {
        TaxStatsSub() { super("stats", "tradeflow.admin", false); }

        @Override
        public boolean execute(CommandSender sender, String[] args) {
            TaxManager taxManager = plugin.getServices().get(TaxManager.class);
            ITaxSettings taxSettings = plugin.getServices().get(ITaxSettings.class);

            if (taxManager == null) {
                Format.sendRawMessage(sender, "<red>Tax system is not available.");
                return true;
            }

            Format.sendRawMessage(sender, "<gold><strikethrough>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            Format.sendRawMessage(sender, "<gold><bold>  Système de Taxes - Statistiques");
            Format.sendRawMessage(sender, "<gold><strikethrough>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            sender.sendMessage(Component.empty());

            boolean enabled = taxSettings != null && taxSettings.isEnabled();
            Format.sendRawMessage(sender, "<gray>Statut: " + (enabled ? "<green>Actif" : "<red>Inactif"));

            if (enabled && taxSettings != null) {
                Format.sendRawMessage(sender, "<gray>Taux par défaut: <yellow>" + (taxSettings.getDefaultTaxRate() * 100) + "%");
                Format.sendRawMessage(sender, "<gray>Taux achat: <yellow>" + (taxSettings.getBuyTaxRate() * 100) + "%");
                Format.sendRawMessage(sender, "<gray>Taux vente: <yellow>" + (taxSettings.getSellTaxRate() * 100) + "%");
                Format.sendRawMessage(sender, "<gray>Taux max: <yellow>" + (taxSettings.getMaximumTaxRate() * 100) + "%");
                Format.sendRawMessage(sender, "<gray>Compte Trésor: <yellow>" + taxSettings.getTreasuryAccount());
            }

            sender.sendMessage(Component.empty());

            double totalCollected = taxManager.getTotalTaxesCollected();
            int recordCount = taxManager.getRecordCount();
            Format.sendRawMessage(sender, "<gray>Total collecté: <green>" + Format.currency(totalCollected));
            Format.sendRawMessage(sender, "<gray>Nombre de transactions taxées: <yellow>" + recordCount);

            long todayStart = ZonedDateTime.now().withHour(0).withMinute(0).withSecond(0).toInstant().toEpochMilli();
            double todayTaxes = taxManager.getTaxesCollectedInPeriod(todayStart, System.currentTimeMillis());
            Format.sendRawMessage(sender, "<gray>Aujourd'hui: <green>" + Format.currency(todayTaxes));

            sender.sendMessage(Component.empty());
            Format.sendRawMessage(sender, "<gray>Utilisez <yellow>/tfadmin taxes top <gray>pour voir les plus gros contributeurs.");
            Format.sendRawMessage(sender, "<gold><strikethrough>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, String[] args) { return List.of(); }
    }

    private class TaxPlayerSub extends SubCommand {
        TaxPlayerSub() { super("player", "tradeflow.admin", false); }

        @Override
        public boolean execute(CommandSender sender, String[] args) {
            if (args.length < 1) {
                Format.sendRawMessage(sender, "<red>Usage: /tfadmin taxes player <joueur>");
                return true;
            }

            TaxManager taxManager = plugin.getServices().get(TaxManager.class);
            if (taxManager == null) {
                Format.sendRawMessage(sender, "<red>Tax system is not available.");
                return true;
            }

            String playerName = args[0];
            UUID playerUuid = findPlayerUuid(plugin, playerName);

            if (playerUuid == null) {
                Format.sendRawMessage(sender, "<red>Joueur introuvable: " + playerName);
                return true;
            }

            List<TaxRecord> records = taxManager.getPlayerTaxRecords(playerUuid);
            double playerTotal = records.stream().mapToDouble(TaxRecord::getTaxAmount).sum();
            double playerVolume = taxManager.getPlayerVolume(playerUuid);

            Format.sendRawMessage(sender, "<gold><strikethrough>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            Format.sendRawMessage(sender, "<gold><bold>  Taxes: " + playerName);
            Format.sendRawMessage(sender, "<gold><strikethrough>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            sender.sendMessage(Component.empty());
            Format.sendRawMessage(sender, "<gray>Volume de transactions: <yellow>" + Format.currency(playerVolume));
            Format.sendRawMessage(sender, "<gray>Total des taxes payées: <green>" + Format.currency(playerTotal));
            Format.sendRawMessage(sender, "<gray>Nombre de transactions taxées: <yellow>" + records.size());

            if (!records.isEmpty()) {
                sender.sendMessage(Component.empty());
                Format.sendRawMessage(sender, "<gray>Dernières transactions taxées:");

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm");
                records.stream()
                    .sorted((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()))
                    .limit(5)
                    .forEach(record -> {
                        ZonedDateTime dateTime = ZonedDateTime.ofInstant(
                            Instant.ofEpochMilli(record.getTimestamp()),
                            ZoneId.systemDefault()
                        );
                        Format.sendRawMessage(sender, "<dark_gray>  <gray>[" + formatter.format(dateTime) + "] <yellow>" +
                            Format.currency(record.getTaxAmount()) + " <gray>(" +
                            (int)(record.getTaxRate() * 100) + "%) <dark_gray>- <gray>" + record.getShopName());
                    });
            }

            sender.sendMessage(Component.empty());
            Format.sendRawMessage(sender, "<gold><strikethrough>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, String[] args) {
            if (args.length == 1) {
                return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName).sorted().toList();
            }
            return List.of();
        }
    }

    private class TaxTopSub extends SubCommand {
        TaxTopSub() { super("top", "tradeflow.admin", false); }

        @Override
        public boolean execute(CommandSender sender, String[] args) {
            TaxManager taxManager = plugin.getServices().get(TaxManager.class);
            if (taxManager == null) {
                Format.sendRawMessage(sender, "<red>Tax system is not available.");
                return true;
            }

            Format.sendRawMessage(sender, "<gold><strikethrough>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            Format.sendRawMessage(sender, "<gold><bold>  Top Contributeurs du Trésor");
            Format.sendRawMessage(sender, "<gold><strikethrough>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            sender.sendMessage(Component.empty());

            Map<UUID, Double> playerTotals = taxManager.getTaxRecords().values().stream()
                .collect(Collectors.groupingBy(
                    TaxRecord::getPlayerUuid,
                    Collectors.summingDouble(TaxRecord::getTaxAmount)
                ));

            playerTotals.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(10)
                .forEach(entry -> {
                    String name = getPlayerName(plugin, entry.getKey());
                    Format.sendRawMessage(sender, "<gray>  <yellow>" + name + "<gray>: <green>" + Format.currency(entry.getValue()));
                });

            sender.sendMessage(Component.empty());
            Format.sendRawMessage(sender, "<gold><strikethrough>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, String[] args) { return List.of(); }
    }

    private class TaxResetSub extends SubCommand {
        TaxResetSub() { super("reset", "tradeflow.admin.tax.reset", false); }

        @Override
        public boolean execute(CommandSender sender, String[] args) {
            if (!sender.hasPermission("tradeflow.admin.tax.reset")) {
                Format.sendRawMessage(sender, "<red>Vous n'avez pas la permission de réinitialiser les taxes.");
                return true;
            }

            TaxManager taxManager = plugin.getServices().get(TaxManager.class);
            if (taxManager == null) {
                Format.sendRawMessage(sender, "<red>Tax system is not available.");
                return true;
            }

            if (args.length < 1) {
                Format.sendRawMessage(sender, "<red>Usage: /tfadmin taxes reset <joueur|all>");
                return true;
            }

            String target = args[0].toLowerCase();

            if (target.equals("all")) {
                taxManager.resetAllVolumes();
                Format.sendRawMessage(sender, "<green>Tous les volumes de taxes ont été réinitialisés.");
            } else {
                UUID playerUuid = findPlayerUuid(plugin, target);
                if (playerUuid == null) {
                    Format.sendRawMessage(sender, "<red>Joueur introuvable: " + target);
                    return true;
                }
                taxManager.resetPlayerVolume(playerUuid);
                Format.sendRawMessage(sender, "<green>Volume de taxes réinitialisé pour: " + target);
            }
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, String[] args) {
            if (args.length == 1) {
                List<String> suggestions = new ArrayList<>();
                suggestions.add("all");
                suggestions.addAll(plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName).sorted().toList());
                return suggestions;
            }
            return List.of();
        }
    }

    private class TaxReloadSub extends SubCommand {
        TaxReloadSub() { super("reload", "tradeflow.admin.tax.reload", false); }

        @Override
        public boolean execute(CommandSender sender, String[] args) {
            if (!sender.hasPermission("tradeflow.admin.tax.reload")) {
                Format.sendRawMessage(sender, "<red>Vous n'avez pas la permission de recharger les taxes.");
                return true;
            }

            boolean success = plugin.getBootstrap().getConfigLoader().reload();
            if (success) {
                Format.sendRawMessage(sender, "<green>Configuration des taxes rechargée.");
            } else {
                Format.sendRawMessage(sender, "<red>Erreur lors du rechargement de la configuration.");
            }
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, String[] args) { return List.of(); }
    }
}
