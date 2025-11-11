package com.github.lye.commands;

import org.bukkit.command.CommandSender;
import com.github.lye.TradeFlow;
import com.github.lye.commands.core.BaseCommand;

import java.util.List;

public class TradeFlowCommand extends BaseCommand {
    public TradeFlowCommand(TradeFlow plugin) {
        super(plugin, "tf", "tradeflow.admin", "TradeFlow main command.", "/tf <subcommand>");
        registerSubCommand(new TradeFlowHelpCommand(plugin));
        registerSubCommand(new TradeFlowAdminHelpCommand(plugin));
        registerSubCommand(new TradeFlowReloadCommand(plugin));
        registerSubCommand(new TradeFlowUpdateCommand(plugin));
        registerSubCommand(new TradeFlowExportCommand(plugin));
        registerSubCommand(new TradeFlowImportCommand(plugin));
        registerSubCommand(new TradeFlowRemoveShopCommand(plugin));
        registerSubCommand(new TradeFlowSetPriceCommand(plugin));
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // This will be the main logic for the /tradeflow command, handling subcommands.
        // For now, BaseCommand's default execute will handle showing usage or dispatching to subcommands.
        return super.execute(sender, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        // This will handle tab completion for /tradeflow subcommands.
        return super.onTabComplete(sender, args);
    }
}