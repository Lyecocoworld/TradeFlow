package com.github.lye.commands;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.github.stefvanschie.inventoryframework.pane.Pane.Priority;
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
import com.github.lye.data.PurchaseUtil;
import com.github.lye.data.Shop;
import com.github.lye.data.ShopUtil;

import com.github.lye.util.Format;
import com.github.lye.commands.core.BaseCommand;
import com.github.lye.gui.ShopGuiManager; // Import ShopGuiManager

/**
 * The command for buying and selling items.
 */
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;

public class MarketCommand extends BaseCommand implements CommandExecutor {

    private final ShopGuiManager shopGuiManager; // Add instance of ShopGuiManager

    public MarketCommand(@NotNull TradeFlow plugin, @NotNull ShopGuiManager shopGuiManager) {
        super(plugin, "tfmarket", "tradeflow.command.market", "Market command.", "/tfmarket <buy|sell|admin>");
        setPlayerOnly(true);

        this.shopGuiManager = shopGuiManager;

        // Register subcommands here (will be created in next steps)
        // registerSubCommand(new ShopBuySubCommand(plugin));
        // registerSubCommand(new ShopSellSubCommand(plugin));
        // registerSubCommand(new ShopAdminSubCommand(plugin));
    }

    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (super.execute(sender, args)) {
            return true;
        }

        // If no subcommand was matched by BaseCommand, or if it's the main /shop command
        if (args.length == 0) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                Format.sendMessage(player, "market-opening-gui"); // Placeholder message key
                shopGuiManager.openMainShopGui(player);
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