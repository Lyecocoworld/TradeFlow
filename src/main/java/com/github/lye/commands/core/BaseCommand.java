package com.github.lye.commands.core;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.github.lye.TradeFlow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public abstract class BaseCommand implements ICommand {
    protected final TradeFlow plugin;
    protected final String name;
    protected final String permission;
    protected final String description;
    protected final String usage;
    protected boolean playerOnly = false;
    protected final List<ICommand> subCommands = new ArrayList<>();

    public BaseCommand(TradeFlow plugin, String name, String permission, String description, String usage) {
        this.plugin = plugin;
        this.name = name;
        this.permission = permission;
        this.description = description;
        this.usage = usage;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPermission() {
        return permission;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getUsage() {
        return usage;
    }

    @Override
    public boolean isPlayerOnly() {
        return playerOnly;
    }

    protected void setPlayerOnly(boolean playerOnly) {
        this.playerOnly = playerOnly;
    }

    @Override
    public List<ICommand> getSubCommands() {
        return subCommands;
    }

    protected void registerSubCommand(ICommand subCommand) {
        this.subCommands.add(subCommand);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (playerOnly && !(sender instanceof Player)) {
            sender.sendMessage("This command can only be executed by a player.");
            return true; // Handled
        }

        if (!sender.hasPermission(permission)) {
            sender.sendMessage("You don't have permission to use this command.");
            return true; // Handled
        }

        if (args.length > 0) {
            for (ICommand sub : subCommands) {
                if (sub.getName().equalsIgnoreCase(args[0])) {
                    return sub.execute(sender, Arrays.copyOfRange(args, 1, args.length)); // Handled by subcommand
                }
            }
        }

        if (subCommands.isEmpty()) {
            return false; // Not handled, let subclass continue
        } else {
            sender.sendMessage(getUsage());
            return true; // Handled (showed usage)
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return subCommands.stream()
                    .filter(sub -> sub.getName().toLowerCase().startsWith(args[0].toLowerCase()))
                    .filter(sub -> sender.hasPermission(sub.getPermission()))
                    .map(ICommand::getName)
                    .collect(Collectors.toList());
        } else if (args.length > 1) {
            for (ICommand sub : subCommands) {
                if (sub.getName().equalsIgnoreCase(args[0])) {
                    return sub.onTabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
                }
            }
        }
        return new ArrayList<>();
    }
}
