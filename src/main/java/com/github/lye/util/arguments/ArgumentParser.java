package com.github.lye.util.arguments;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.github.lye.config.Config;
import com.github.lye.data.Database;
import com.github.lye.data.Shop;
import com.github.lye.data.ShopUtil;
import com.github.lye.config.settings.IMessageSettings;
import com.github.lye.util.Format;
import com.github.lye.service.IMessageService;

import java.util.Optional;

public class ArgumentParser {

    private final IMessageService messageService;

    public ArgumentParser(IMessageService messageService) {
        this.messageService = messageService;
    }

    public static Optional<Player> getPlayer(CommandSender sender, IMessageService messageService, String arg) {
        Player player = Bukkit.getPlayer(arg);
        if (player == null) {
            messageService.sendErrorMessage(sender, "player-not-found", Placeholder.parsed("player", arg)); // Need to add this message key
            return Optional.empty();
        }
        return Optional.of(player);
    }

    public static Optional<Double> getDouble(CommandSender sender, IMessageService messageService, String arg, String errorMessageKey) {
        try {
            return Optional.of(Double.parseDouble(arg));
        } catch (NumberFormatException e) {
            messageService.sendErrorMessage(sender, errorMessageKey, null);
            return Optional.empty();
        }
    }

    public static Optional<Integer> getInteger(CommandSender sender, IMessageService messageService, String arg, String errorMessageKey) {
        try {
            return Optional.of(Integer.parseInt(arg));
        } catch (NumberFormatException e) {
            messageService.sendErrorMessage(sender, errorMessageKey, null);
            return Optional.empty();
        }
    }

    public static Optional<Shop> getShop(ShopUtil shopUtil, IMessageSettings messageSettings, CommandSender sender, IMessageService messageService, String arg) {
        Shop shop = shopUtil.getShop(arg, true);
        if (shop == null) {
            messageService.sendErrorMessage(sender, messageSettings.getNotInShop(), null);
            return Optional.empty();
        }
        return Optional.of(shop);
    }
}
