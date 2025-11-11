package com.github.lye.util.arguments;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.github.lye.config.Config;
import com.github.lye.data.Database;
import com.github.lye.data.Shop;
import com.github.lye.data.ShopUtil;
import com.github.lye.util.Format;

import java.util.Optional;

public class ArgumentParser {

    public static Optional<Player> getPlayer(CommandSender sender, String arg) {
        Player player = Bukkit.getPlayer(arg);
        if (player == null) {
            Format.sendMessage(sender, "player-not-found", Placeholder.parsed("player", arg)); // Need to add this message key
            return Optional.empty();
        }
        return Optional.of(player);
    }

    public static Optional<Double> getDouble(CommandSender sender, String arg, String errorMessageKey) {
        try {
            return Optional.of(Double.parseDouble(arg));
        } catch (NumberFormatException e) {
            Format.sendMessage(sender, errorMessageKey);
            return Optional.empty();
        }
    }

    public static Optional<Integer> getInteger(CommandSender sender, String arg, String errorMessageKey) {
        try {
            return Optional.of(Integer.parseInt(arg));
        } catch (NumberFormatException e) {
            Format.sendMessage(sender, errorMessageKey);
            return Optional.empty();
        }
    }

    public static Optional<Shop> getShop(Database database, CommandSender sender, String arg) {
        Shop shop = ShopUtil.getShop(database, arg, true);
        if (shop == null) {
            Format.sendMessage(sender, Config.get().getNotInShop());
            return Optional.empty();
        }
        return Optional.of(shop);
    }
}
