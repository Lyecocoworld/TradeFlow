package com.github.lye.commands;

import com.github.lye.TradeFlow;
import com.github.lye.commands.core.SubCommand;
import com.github.lye.gameplay.rumors.RumorManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class RumorCommand extends SubCommand {

    private final TradeFlow plugin;
    private final RumorManager rumorManager;

    public RumorCommand(TradeFlow plugin) {
        super("rumor", "tradeflow.rumor", false);
        this.plugin = plugin;
        this.rumorManager = plugin.getRumorManager();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(getPermission())) {
            plugin.getMessageService().sendErrorMessage(sender, "permission-denied", null);
            return true;
        }
        if (!(sender instanceof Player)) {
            plugin.getMessageService().sendErrorMessage(sender, "players-only", null);
            return true;
        }
        Player player = (Player) sender;
        
        // If they are close, canAccessBroker returns true.
        // Directly open the Rumor GUI for better UX.
        if (rumorManager.canAccessBroker(player)) {
            new com.github.lye.gui.RumorGui(plugin, player).open(player);
        }
        return true;
    }

    public RumorManager getManager() {
        return rumorManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return java.util.Arrays.asList("cheap", "standard", "insider");
        }
        return Collections.emptyList();
    }
}
