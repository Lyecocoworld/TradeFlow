package unprotesting.com.github.commands;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import unprotesting.com.github.AutoTune;
import unprotesting.com.github.commands.core.BaseCommand;

import java.util.List;

public class LoanCommand extends BaseCommand {

    public LoanCommand(@NotNull AutoTune plugin) {
        super(plugin, "atloan", "autotune.command.loan", "Manage your loans.", "/atloan <info|pay|take>");
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