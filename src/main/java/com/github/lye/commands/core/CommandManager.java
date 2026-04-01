package com.github.lye.commands.core;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import com.github.lye.TradeFlow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandManager implements CommandExecutor, TabCompleter {
    private final TradeFlow plugin;
    private final Map<String, ICommand> commands = new HashMap<>();

    public CommandManager(TradeFlow plugin) {
        this.plugin = plugin;
        registerCommand(new com.github.lye.commands.RumorCommand(plugin));
        registerCommand(new com.github.lye.commands.BlackMarketCommand(plugin));
    }

    public void registerCommand(ICommand command) {
        commands.put(command.getName().toLowerCase(), command);
    }

    public Map<String, ICommand> getCommands() {
        return commands;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String commandName = cmd.getName().toLowerCase(); // "tradeflow" or "tfadmin"

        // First, try to find a matching main command in our registry
        ICommand mainCommand = commands.get(commandName);
        if (mainCommand != null) {
            return mainCommand.execute(sender, args);
        }

        // Handle subcommands for direct calls
        if (args.length > 0) {
            ICommand subCommand = commands.get(args[0].toLowerCase());
            if (subCommand != null) {
                return subCommand.execute(sender, args);
            }
        }

        // No matching subcommand - show help or default
        ICommand defaultCommand = commands.get("help");
        if (defaultCommand != null) {
            return defaultCommand.execute(sender, args);
        }

        // Fallback: send to TradeFlowCommand for help display
        ICommand tfCommand = commands.get("tradeflow");
        if (tfCommand != null) {
            return tfCommand.execute(sender, args);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        String commandName = cmd.getName().toLowerCase();

        // Get the main command being executed
        ICommand mainCommand = commands.get(commandName);
        if (mainCommand != null) {
            // Delegate to the main command's tab completer (which handles its subcommands)
            return mainCommand.onTabComplete(sender, args);
        }

        return new ArrayList<>();
    }
}
