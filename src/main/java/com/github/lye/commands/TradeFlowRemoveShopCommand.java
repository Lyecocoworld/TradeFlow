package com.github.lye.commands;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import com.github.lye.TradeFlow;
import com.github.lye.commands.core.BaseCommand;
import com.github.lye.config.Config;
import com.github.lye.data.Database;
import com.github.lye.data.ShopUtil;
import com.github.lye.util.Format;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TradeFlowRemoveShopCommand extends BaseCommand {

    public TradeFlowRemoveShopCommand(@NotNull TradeFlow plugin) {
        super(plugin, "removeshop", "tradeflow.admin", "Remove a shop.", "/tradeflow removeshop <shopName>");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (super.execute(sender, args)) {
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(getUsage());
            return true;
        }

        String shopName = args[0].toLowerCase();

        Database.acquireWriteLock();
        try {
            if (ShopUtil.removeShop(shopName)) {
                Format.sendMessage(sender, Config.get().getAdminShopRemoved());
            } else {
                Format.sendMessage(sender, Config.get().getAdminShopNotFound());
            }
        } finally {
            Database.releaseWriteLock();
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.stream(ShopUtil.getShopNames(plugin.getDatabase()))
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return super.onTabComplete(sender, args);
    }
}
