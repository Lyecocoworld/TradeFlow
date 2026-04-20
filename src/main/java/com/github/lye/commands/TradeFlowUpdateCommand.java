package com.github.lye.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import com.github.lye.TradeFlow;
import com.github.lye.commands.core.BaseCommand;
import com.github.lye.config.Config;
import com.github.lye.service.IMessageService;
import com.github.lye.config.settings.IMessageSettings;

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
            plugin.getServices().get(IMessageService.class).sendInfoMessage(sender, getUsage(), null);
            return true;
        }

        if (sender instanceof Player) {
            Player player = (Player) sender;
            IMessageService msgSvc = plugin.getServices().get(IMessageService.class);
            IMessageSettings msgSettings = plugin.getServices().get(IMessageSettings.class);
            msgSvc.sendInfoMessage(player, msgSettings.getAdminPricesUpdating(), null);
            plugin.recalculatePrices();
            Format.sendRawMessage(player, "<green>Auto-pricing snapshot recomputed.");
        } else {
            plugin.getServices().get(IMessageService.class).sendInfoMessage(sender, plugin.getServices().get(IMessageSettings.class).getPlayersOnly(), null);
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
