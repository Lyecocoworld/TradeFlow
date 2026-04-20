package com.github.lye.commands;

import com.github.lye.TradeFlow;
import com.github.lye.commands.core.SubCommand;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Collections;
import java.util.List;

public class TradeFlowTpBrokerCommand extends SubCommand {

    private final TradeFlow plugin;

    public TradeFlowTpBrokerCommand(TradeFlow plugin) {
        super("tpbroker", "tradeflow.admin", true);
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can teleport.");
            return true;
        }
        Player player = (Player) sender;

        Location brokerLoc = plugin.getServices().get(com.github.lye.gameplay.rumors.RumorManager.class).getCurrentBrokerLocation();
        if (brokerLoc == null) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>The Shadow Broker is currently hidden (Day time or not spawned).</red>"));
            return true;
        }

        player.teleportAsync(brokerLoc);
        player.sendMessage(MiniMessage.miniMessage().deserialize("<green>Teleported to the Shadow Broker.</green>"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
