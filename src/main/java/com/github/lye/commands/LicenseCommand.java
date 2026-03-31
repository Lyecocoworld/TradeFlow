package com.github.lye.commands;

import com.github.lye.TradeFlow;
import com.github.lye.commands.core.SubCommand;
import com.github.lye.gui.LicenseGui;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class LicenseCommand extends SubCommand {

    private final TradeFlow plugin;

    public LicenseCommand(TradeFlow plugin) {
        super("license", "tradeflow.license", true); // Permission required? Yes, usually basic access
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        Player player = (Player) sender;
        new LicenseGui(plugin, player).open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
