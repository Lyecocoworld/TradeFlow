package com.github.lye.commands;

import com.github.lye.TradeFlow;
import com.github.lye.commands.core.BaseCommand;
import com.github.lye.config.settings.IMessageSettings;
import com.github.lye.gui.HelpGui;
import com.github.lye.service.IMessageService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TradeFlowHelpCommand extends BaseCommand {

    public TradeFlowHelpCommand(@NotNull TradeFlow plugin) {
        super(plugin, "help", "tradeflow.help", "Display TradeFlow help messages.", "/tradeflow help");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        // Check permission via super
        if (super.execute(sender, args)) {
            return true;
        }

        if (sender instanceof Player) {
            Player player = (Player) sender;
            new HelpGui(plugin, player).open(player);
            return true;
        }

        // Console fallback
        for (String message : plugin.getServices().get(IMessageSettings.class).getHelp()) {
            plugin.getServices().get(IMessageService.class).sendInfoMessage(sender, message, null);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return super.onTabComplete(sender, args);
    }
}
