package com.github.lye.commands;

import com.github.lye.TradeFlow;
import com.github.lye.commands.core.SubCommand;
import com.github.lye.gui.BlackMarketGui;
import com.github.lye.gameplay.rumors.RumorManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * Black Market command - Opens the black market GUI.
 * <p>
 * Usage: /tf blackmarket or /blackmarket
 * <br>Requires: tradeflow.blackmarket permission
 * <br>Access control: Players must be granted access via the rumor/broker system</p>
 *
 * @author  lye
 * @since   0.1
 */
public class BlackMarketCommand extends SubCommand {

    private final TradeFlow plugin;

    public BlackMarketCommand(TradeFlow plugin) {
        super("blackmarket", "tradeflow.blackmarket", false);
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // Permission check
        if (!sender.hasPermission(getPermission())) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<red><b>Accès refusé</b></red> <gray>Vous n'avez pas la permission d'accéder au Marché Noir.</gray>"
            ));
            return true;
        }

        // Player-only check
        if (!(sender instanceof Player)) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<red>Cette commande ne peut être utilisée que par un joueur.</red>"
            ));
            return true;
        }

        Player player = (Player) sender;

        // Access control check via RumorManager
        RumorManager rumorManager = plugin.getRumorManager();
        if (rumorManager != null && !rumorManager.canAccessBroker(player)) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<dark_gray><b>⚠ Marché Noir</b></dark_gray>"
            ));
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<gray>Le Marché Noir est actuellement inaccessible. Vous devez d'abord trouver le <gold>Frateur</gold>..."
            ));
            return true;
        }

        // Open black market GUI
        new BlackMarketGui(plugin, player).open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
