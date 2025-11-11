package com.github.lye.commands;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import com.github.lye.TradeFlow;
import com.github.lye.commands.core.BaseCommand;
import com.github.lye.config.Config;
import com.github.lye.data.Database;
import com.github.lye.data.Shop;
import com.github.lye.data.ShopUtil;
import com.github.lye.util.Format;
import com.github.lye.util.arguments.ArgumentParser; // Import ArgumentParser

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;

public class TradeFlowSetPriceCommand extends BaseCommand implements CommandExecutor {

    public TradeFlowSetPriceCommand(@NotNull TradeFlow plugin) {
        super(plugin, "setprice", "tradeflow.admin", "Set the price of a shop item.", "/tradeflow setprice <shopName> <price>");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (super.execute(sender, args)) {
            return true;
        }

        if (args.length != 2) {
            Format.sendMessage(sender, getUsage());
            return true;
        }

        String shopName = args[0];
        
        Optional<Double> priceOptional = ArgumentParser.getDouble(sender, args[1], Config.get().getAdminInvalidPrice());
        if (priceOptional.isEmpty()) {
            return true;
        }
        double price = priceOptional.get();

        if (price < 0) {
            Format.sendMessage(sender, Config.get().getAdminInvalidPrice());
            return true;
        }

        Database.acquireWriteLock();
        try {
            Optional<Shop> shopOptional = ArgumentParser.getShop(plugin.getDatabase(), sender, shopName);
            if (shopOptional.isEmpty()) {
                return true;
            }
            Shop shop = shopOptional.get();

            shop.setPrice(price);
            ShopUtil.putShop(plugin.getDatabase(), shopName, shop);
            plugin.recalculatePrices(); // ✅ recalcul des prix dispo publiés dans le PriceService
            TagResolver resolver = Placeholder.parsed("price", Format.currency(price));
            Format.sendMessage(sender, Config.get().getAdminPriceSet(), resolver);
            Format.sendMessage(sender, "§aPrices recalculation requested.");
        } finally {
            Database.releaseWriteLock();
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.stream(ShopUtil.getShopNames(plugin.getDatabase()))
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            // Suggest common prices or a placeholder
            return List.of("1.0", "10.0", "100.0");
        }
        return super.onTabComplete(sender, args);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players can run this.");
            return true;
        }
        if (!p.hasPermission("tradeflow.admin")) {
            p.sendMessage("§cYou don't have permission.");
            return true;
        }

        // Call the execute method of BaseCommand
        return execute(sender, args);
    }
}