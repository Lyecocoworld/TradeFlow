package unprotesting.com.github.commands;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import unprotesting.com.github.AutoTune;
import unprotesting.com.github.commands.core.BaseCommand;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.Database;
import unprotesting.com.github.data.Shop;
import unprotesting.com.github.data.ShopUtil;
import unprotesting.com.github.util.Format;
import unprotesting.com.github.util.arguments.ArgumentParser; // Import ArgumentParser

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class AutoTuneSetPriceCommand extends BaseCommand {

    public AutoTuneSetPriceCommand(@NotNull AutoTune plugin) {
        super(plugin, "setprice", "autotune.admin", "Set the price of a shop item.", "/autotune setprice <shopName> <price>");
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
            Optional<Shop> shopOptional = ArgumentParser.getShop(sender, shopName);
            if (shopOptional.isEmpty()) {
                return true;
            }
            Shop shop = shopOptional.get();

            shop.setPrice(price);
            ShopUtil.putShop(shopName, shop);
            plugin.recalculatePrices();
            TagResolver resolver = Placeholder.parsed("price", Format.currency(price));
            Format.sendMessage(sender, Config.get().getAdminPriceSet(), resolver);
        } finally {
            Database.releaseWriteLock();
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.stream(ShopUtil.getShopNames())
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            // Suggest common prices or a placeholder
            return List.of("1.0", "10.0", "100.0");
        }
        return super.onTabComplete(sender, args);
    }
}