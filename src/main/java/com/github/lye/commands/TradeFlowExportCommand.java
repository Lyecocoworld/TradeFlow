package com.github.lye.commands;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import com.github.lye.TradeFlow;
import com.github.lye.commands.core.BaseCommand;
import com.github.lye.config.TxtHandler;

import java.util.List;

public class TradeFlowExportCommand extends BaseCommand {

    public TradeFlowExportCommand(@NotNull TradeFlow plugin) {
        super(plugin, "export", "tradeflow.admin", "Export prices to a text file.", "/tradeflow export");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (super.execute(sender, args)) {
            return true;
        }

        if (args.length > 0) {
            plugin.getMessageService().sendInfoMessage(sender, getUsage(), null);
            return true;
        }

        TxtHandler.exportPrices(plugin.getDatabase(), plugin.getShopUtil(), plugin.getTradeLogger(), plugin.getDataFolder());
        plugin.getMessageService().sendInfoMessage(sender, plugin.getMessageSettings().getAdminPricesExported(), null);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return super.onTabComplete(sender, args);
    }
}
