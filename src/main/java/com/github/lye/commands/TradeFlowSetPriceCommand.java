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
import com.github.lye.service.IMessageService;
import com.github.lye.config.settings.IMessageSettings;
import com.github.lye.util.Format;
import com.github.lye.util.arguments.ArgumentParser;

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
            plugin.getServices().get(IMessageService.class).sendInfoMessage(sender, getUsage(), null);
            return true;
        }

        String shopName = args[0];
        
        IMessageService messageService = plugin.getServices().get(IMessageService.class);
        IMessageSettings messageSettings = plugin.getServices().get(IMessageSettings.class);

        Optional<Double> priceOptional = ArgumentParser.getDouble(sender, messageService, args[1], messageSettings.getAdminInvalidPrice());
        if (priceOptional.isEmpty()) {
            return true;
        }
        double price = priceOptional.get();

        if (price < 0) {
            messageService.sendErrorMessage(sender, messageSettings.getAdminInvalidPrice(), null);
            return true;
        }

        Database.acquireWriteLock();
        try {
            Optional<Shop> shopOptional = ArgumentParser.getShop(plugin.getServices().get(ShopUtil.class), messageSettings, sender, messageService, shopName);
            if (shopOptional.isEmpty()) {
                return true;
            }
            Shop shop = shopOptional.get();

            shop.setPrice(price);
            plugin.getServices().get(ShopUtil.class).putShop(shopName, shop);
            plugin.recalculatePrices();
            TagResolver resolver = Placeholder.parsed("price", Format.currency(price));
            messageService.sendInfoMessage(sender, messageSettings.getAdminPriceSet(), resolver);
            Format.sendRawMessage(sender, "<green>Prices recalculation requested.");
        } finally {
            Database.releaseWriteLock();
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.stream(plugin.getServices().get(ShopUtil.class).getShopNames())
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
            Format.sendRawMessage(p, "<red>You don't have permission.");
            return true;
        }

        // Call the execute method of BaseCommand
        return execute(sender, args);
    }
}
