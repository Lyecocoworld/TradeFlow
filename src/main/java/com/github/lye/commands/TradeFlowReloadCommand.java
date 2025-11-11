package com.github.lye.commands;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import com.github.lye.TradeFlow;
import com.github.lye.commands.core.BaseCommand;
import com.github.lye.config.Config;
import com.github.lye.data.Database;
import com.github.lye.data.ShopUtil;
import com.github.lye.util.Format;

import java.util.List;

public class TradeFlowReloadCommand extends BaseCommand {

    public TradeFlowReloadCommand(@NotNull TradeFlow plugin) {
        super(plugin, "reload", "tradeflow.admin", "Reload shops configuration.", "/tradeflow reload");
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

        Config config = Config.get();
        Format.sendMessage(sender, config.getAdminReloadingShops());
        Database.acquireWriteLock();
        try {
            ShopUtil.reload();
        } finally {
            Database.releaseWriteLock();
        }
        Format.sendMessage(sender, config.getAdminShopsReloaded());
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return super.onTabComplete(sender, args);
    }
}
