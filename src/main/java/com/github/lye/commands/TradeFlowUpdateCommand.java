package com.github.lye.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import com.github.lye.TradeFlow;
import com.github.lye.commands.core.BaseCommand;
import com.github.lye.config.Config;

import com.github.lye.util.Format;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;

public class TradeFlowUpdateCommand extends BaseCommand implements CommandExecutor {

    public TradeFlowUpdateCommand(@NotNull TradeFlow plugin) {
        super(plugin, "update", "tradeflow.admin", "Update prices.", "/tradeflow update");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (super.execute(sender, args)) {
            return true;
        }

        if (args.length > 0) {
            Format.sendMessage(sender, getUsage());
            return true;
        }

        if (sender instanceof Player) {
            Player player = (Player) sender;
            Format.sendMessage(player, Config.get().getAdminPricesUpdating());
            plugin.recalculatePrices(); // ✅
            player.sendMessage("§aAuto-pricing snapshot recomputed.");
        } else {
            Format.sendMessage(sender, Config.get().getPlayersOnly()); // Use message key
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return super.onTabComplete(sender, args);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        // The execute method already handles permission and player check
        return execute(sender, args);
    }
}