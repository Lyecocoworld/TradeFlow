package com.github.lye.commands;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import com.github.lye.TradeFlow;
import com.github.lye.commands.core.BaseCommand;
import com.github.lye.config.Config;
import com.github.lye.config.TxtHandler;
import com.github.lye.util.Format;

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

        TxtHandler.importPrices(plugin.getDatabase());
        Format.sendMessage(sender, Config.get().getAdminPricesImported());
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return super.onTabComplete(sender, args);
    }
}
