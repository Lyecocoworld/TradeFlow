package com.github.lye.commands;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import com.github.lye.TradeFlow;
import com.github.lye.commands.core.BaseCommand;
import com.github.lye.config.Config;
import com.github.lye.config.settings.IMessageSettings;
import com.github.lye.service.IMessageService;
import com.github.lye.util.Format;

import java.util.List;

public class TradeFlowAdminHelpCommand extends BaseCommand {

    public TradeFlowAdminHelpCommand(@NotNull TradeFlow plugin) {
        super(plugin, "adminhelp", "tradeflow.admin", "Display TradeFlow admin help messages.", "/tradeflow adminhelp");
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

        for (String message : plugin.getServices().get(IMessageSettings.class).getAdminHelp()) {
            plugin.getServices().get(IMessageService.class).sendInfoMessage(sender, message, null);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return super.onTabComplete(sender, args);
    }
}
