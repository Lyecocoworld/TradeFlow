package com.github.lye.commands;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import com.github.lye.TradeFlow;
import com.github.lye.commands.core.BaseCommand;
import com.github.lye.config.TxtHandler;
import com.github.lye.config.settings.IMessageSettings;
import com.github.lye.data.Database;
import com.github.lye.data.ShopUtil;
import com.github.lye.service.IMessageService;
import com.github.lye.util.TradeFlowLogger;

import java.util.List;

public class TradeFlowImportCommand extends BaseCommand {

    public TradeFlowImportCommand(@NotNull TradeFlow plugin) {
        super(plugin, "import", "tradeflow.admin", "Import prices from a text file.", "/tradeflow import");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (super.execute(sender, args)) {
            return true;
        }

        if (args.length > 0) {
            sender.sendMessage(getUsage());
            return true;
        }

        TxtHandler.importPrices(plugin.getServices().get(Database.class), plugin.getServices().get(ShopUtil.class), plugin.getServices().get(TradeFlowLogger.class), plugin.getDataFolder());
        plugin.getServices().get(IMessageService.class).sendInfoMessage(sender, plugin.getServices().get(IMessageSettings.class).getAdminPricesImported(), null);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return super.onTabComplete(sender, args);
    }
}
