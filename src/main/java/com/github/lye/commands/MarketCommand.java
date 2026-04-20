package com.github.lye.commands;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import com.github.lye.TradeFlow;
import com.github.lye.config.Config;
import com.github.lye.data.Shop;
import com.github.lye.data.ShopUtil;
import com.github.lye.gui.GuiNavigator;
import com.github.lye.service.IMessageService;

import com.github.lye.util.Format;
import com.github.lye.commands.core.BaseCommand;
import com.github.lye.gui.GuiNavigator;

/**
 * The command for buying and selling items.
 */
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;

public class MarketCommand extends BaseCommand implements CommandExecutor {

    private final GuiNavigator guiNavigator; 

    public MarketCommand(TradeFlow plugin) {
        super(plugin, "market", "tradeflow.command.market", "View market information.", "/market");
        this.guiNavigator = plugin.getServices().get(GuiNavigator.class);
    }

    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (super.execute(sender, args)) {
            return true;
        }

        // If no subcommand was matched by BaseCommand, or if it's the main /shop command
        if (args.length == 0) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (!player.hasPermission("tradeflow.use")) {
                    plugin.getServices().get(IMessageService.class).sendErrorMessage(player, "permission-denied", null);
                    return true;
                }
                plugin.getLogger().info("MarketCommand: Executing for player " + player.getName());
                plugin.getServices().get(IMessageService.class).sendInfoMessage(player, "market-opening-gui", null);
                guiNavigator.openMain(player);
            } else {
                sender.sendMessage("Only players can use this command.");
            }
            return true;
        }

        // If BaseCommand didn't handle a subcommand, and there are args, it means an invalid subcommand
        sender.sendMessage(getUsage());
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return super.onTabComplete(sender, args);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        // The execute method already handles permission and player check
        return execute(sender, args);
    }
}
