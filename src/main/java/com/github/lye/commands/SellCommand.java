package com.github.lye.commands;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import com.github.lye.TradeFlow;
import com.github.lye.config.Config;
import com.github.lye.data.PurchaseUtil;
import com.github.lye.data.Shop;
import com.github.lye.data.ShopUtil;
import com.github.lye.util.EconomyUtil;
import com.github.lye.util.Format;
import com.github.lye.data.Transaction;
import com.github.lye.commands.core.BaseCommand; // Import BaseCommand

import java.util.ArrayList;
import java.util.List;

/**
 * The command for selling items.
 */
public class SellCommand extends BaseCommand implements CommandExecutor, TabCompleter {

    public SellCommand(@NotNull TradeFlow plugin) {
        super(plugin, "tfsell", "tradeflow.command.sell", "Open a panel to sell items.", "/tfsell");
        setPlayerOnly(true);
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        // BaseCommand handles playerOnly and permission checks
        if (super.execute(sender, args)) {
            return true;
        }

        Player player = (Player) sender; // Cast is safe due to playerOnly = true

        if (args.length > 0) {
            sender.sendMessage(getUsage());
            return true;
        }

        // Logic from the original interpret method
        // Temporary placeholder during GUI migration to DevNatan IF.
        // The sell panel will be re-implemented as a View.
        plugin.getGuiNavigator().openMain(player);
        return true;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        return execute(sender, args);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        return onTabComplete(sender, args);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        // This command doesn't take arguments, so no tab completion needed.
        return super.onTabComplete(sender, args); // BaseCommand will return empty list
    }
}
