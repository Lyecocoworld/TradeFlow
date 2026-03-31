package com.github.lye.commands;

import com.github.lye.TradeFlow;
import com.github.lye.commands.core.BaseCommand;
import com.github.lye.gui.AdminNavigator;
import com.github.lye.data.CentralBankStockManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Main admin command for TradeFlow.
 * <p>
 * Usage: /tfadmin [subcommand]
 * <br>Without arguments: Opens the admin menu (for players)
 * <br>With subcommand: Executes the subcommand</p>
 *
 * @author  lye
 * @since   0.1
 */
public class TradeFlowAdminCommand extends BaseCommand {

    private final AdminNavigator adminNavigator;

    public TradeFlowAdminCommand(TradeFlow plugin, AdminNavigator adminNavigator) {
        super(plugin, "tfadmin", "tradeflow.admin", "TradeFlow administration commands.", "/tfadmin [subcommand]");
        this.adminNavigator = adminNavigator;
        // Register admin-specific subcommands
        registerSubCommand(new TradeFlowAdminHelpCommand(plugin));
        registerSubCommand(new TradeFlowReloadCommand(plugin));
        registerSubCommand(new TradeFlowUpdateCommand(plugin));
        registerSubCommand(new TradeFlowExportCommand(plugin));
        registerSubCommand(new TradeFlowImportCommand(plugin));
        registerSubCommand(new TradeFlowRemoveShopCommand(plugin));
        registerSubCommand(new TradeFlowSetPriceCommand(plugin));
        registerSubCommand(new TradeFlowTpBrokerCommand(plugin));
        registerSubCommand(new TradeFlowResetCollectionCommand(plugin));
        registerSubCommand(new TradeFlowSetBankBalanceCommand(plugin));
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // Separated admin menu: do not open GUI automatically
        if (args.length == 0) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Utilisation: /tfadmin <subcommand>"));
            return true;
        }

        // Handle "bank collapse" specific subcommand here or in a separate class
        if (args[0].equalsIgnoreCase("bank") && args.length > 1 && args[1].equalsIgnoreCase("collapse")) {
             handleBankCollapse(sender, args);
             return true;
        }

        // Otherwise use default command handling
        return super.execute(sender, args);
    }
    
    private void handleBankCollapse(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Usage: /tfadmin bank collapse <duration> (ex: 10m, 1h)"));
            return;
        }
        
        String durationStr = args[2];
        long seconds = parseDuration(durationStr);
        
        if (seconds <= 0) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Durée invalide. Utilisez 10s, 5m, 1h..."));
            return;
        }
        
        CentralBankStockManager bankManager = plugin.getServices().get(CentralBankStockManager.class);
        if (bankManager == null) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Erreur: Banque Centrale non chargée."));
            return;
        }
        
        new com.github.lye.gameplay.economy.BankCollapseTask(plugin, bankManager, seconds).start();
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>Effondrement de la banque initié pour " + durationStr));
    }

    private long parseDuration(String input) {
        try {
            long multiplier = 1;
            if (input.endsWith("s")) {
                input = input.substring(0, input.length() - 1);
            } else if (input.endsWith("m")) {
                input = input.substring(0, input.length() - 1);
                multiplier = 60;
            } else if (input.endsWith("h")) {
                input = input.substring(0, input.length() - 1);
                multiplier = 3600;
            } else if (input.endsWith("d")) {
                input = input.substring(0, input.length() - 1);
                multiplier = 86400;
            }
            return Long.parseLong(input) * multiplier;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return super.onTabComplete(sender, args);
    }
}
