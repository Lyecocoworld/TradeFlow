package com.github.lye.commands.core;

import org.bukkit.command.CommandSender;
import java.util.Collections;
import java.util.List;

public abstract class SubCommand implements ICommand {
    private final String name;
    private final String permission;
    private final boolean playerOnly;

    public SubCommand(String name, String permission, boolean playerOnly) {
        this.name = name;
        this.permission = permission;
        this.playerOnly = playerOnly;
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
        return "TradeFlow subcommand";
    }

    @Override
    public String getUsage() {
        return "/" + name;
    }

    @Override
    public boolean isPlayerOnly() {
        return playerOnly;
    }

    @Override
    public List<ICommand> getSubCommands() {
        return Collections.emptyList();
    }

    @Override
    public abstract boolean execute(CommandSender sender, String[] args);

    @Override
    public abstract List<String> onTabComplete(CommandSender sender, String[] args);
}
