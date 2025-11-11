package com.github.lye.commands;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import com.github.lye.TradeFlow;
import com.github.lye.commands.core.BaseCommand;
import com.github.lye.config.Config;
import com.github.lye.util.Format;

import java.util.List;

public class TradeFlowHelpCommand extends BaseCommand {

    public TradeFlowHelpCommand(@NotNull TradeFlow plugin) {
        super(plugin, "help", "tradeflow.help", "Display TradeFlow help messages.", "/tradeflow help");
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

        Config config = Config.get();
        for (String message : config.getHelp()) {
            Format.sendMessage(sender, message);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return super.onTabComplete(sender, args);
    }
}
