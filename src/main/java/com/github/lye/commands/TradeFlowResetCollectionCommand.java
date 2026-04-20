package com.github.lye.commands;

import com.github.lye.TradeFlow;
import com.github.lye.commands.core.BaseCommand;
import com.github.lye.gateway.AccessGateway;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class TradeFlowResetCollectionCommand extends BaseCommand {

    public TradeFlowResetCollectionCommand(TradeFlow plugin) {
        super(plugin, "resetcollection", "tradeflow.admin", "Reset collection data for a player.", "/tfadmin resetcollection <player> [item]");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("Usage: " + getUsage());
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("Player not found.");
            return true;
        }

        String targetItem = args.length > 1 ? args[1].toLowerCase() : null;

        // Use the storage abstraction layer to support both File and MySQL
        plugin.getBootstrap().getDatabaseBootstrap().getPlayerCollectionData().resetPlayerCollection(target.getUniqueId(), targetItem);
        
        // Invalidate cache
        plugin.getServices().get(AccessGateway.class).invalidateCache(target.getUniqueId());

        if (targetItem == null) {
            sender.sendMessage("Wiped ALL collection data for " + target.getName());
        } else {
            sender.sendMessage("Wiped collection data for " + targetItem + " for " + target.getName());
        }

        return true;
    }
}
