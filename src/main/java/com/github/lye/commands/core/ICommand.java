package com.github.lye.commands.core;

import org.bukkit.command.CommandSender;
import java.util.List;

public interface ICommand {
    String getName();
    String getPermission();
    String getDescription();
    String getUsage();
    boolean isPlayerOnly();
    List<ICommand> getSubCommands();
    boolean execute(CommandSender sender, String[] args);
    List<String> onTabComplete(CommandSender sender, String[] args);
}
