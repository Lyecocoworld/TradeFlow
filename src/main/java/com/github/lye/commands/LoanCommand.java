package com.github.lye.commands;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import com.github.lye.TradeFlow;
import com.github.lye.commands.core.BaseCommand;

import java.util.List;

public class LoanCommand extends BaseCommand {

    public LoanCommand(@NotNull TradeFlow plugin) {
        super(plugin, "tfloan", "tradeflow.command.loan", "Manage your loans.", "/tfloan <info|pay|take>");
        setPlayerOnly(true);

        // Register subcommands
        registerSubCommand(new LoanInfoCommand(plugin));
        registerSubCommand(new LoanPayCommand(plugin));
        registerSubCommand(new LoanTakeCommand(plugin));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        // BaseCommand handles playerOnly, permission checks, and subcommand dispatching
        // If no subcommand is provided or matched, BaseCommand will show the usage.
        return super.execute(sender, args);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        // BaseCommand handles tab completion for subcommands
        return super.onTabComplete(sender, args);
    }
}