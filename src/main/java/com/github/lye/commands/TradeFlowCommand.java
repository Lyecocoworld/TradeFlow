package com.github.lye.commands;

import com.github.lye.TradeFlow;
import com.github.lye.commands.core.BaseCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Main TradeFlow command for players.
 * <p>
 * Usage: /tf [subcommand]
 * <br>Without arguments: Opens the main shop GUI
 * <br>With subcommand: Executes the subcommand (market, help, etc.)</p>
 *
 * @author  lye
 * @since   0.1
 */
public class TradeFlowCommand extends BaseCommand {

    public TradeFlowCommand(TradeFlow plugin) {
        super(plugin, "tradeflow", "tradeflow.command.user", "TradeFlow main command.", "/tf [subcommand]");
        // Register subcommands
        registerSubCommand(new TradeFlowHelpCommand(plugin));
        // Player-facing commands (still available via /tf <subcommand>)
        registerSubCommand(new MarketCommand(plugin));
        registerSubCommand(new BlackMarketCommand(plugin));
        registerSubCommand(new RumorCommand(plugin));
        registerSubCommand(new SellCommand(plugin));
        registerSubCommand(new LoanCommand(plugin));
        registerSubCommand(new LicenseCommand(plugin));
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // If player and no args, open main shop GUI
        if (sender instanceof Player && args.length == 0) {
            plugin.getGuiNavigator().openMain((Player) sender);
            return true;
        }

        // Otherwise use default command handling
        return super.execute(sender, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return super.onTabComplete(sender, args);
    }
}
