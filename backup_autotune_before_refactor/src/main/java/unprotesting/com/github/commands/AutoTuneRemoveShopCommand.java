package unprotesting.com.github.commands;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import unprotesting.com.github.AutoTune;
import unprotesting.com.github.commands.core.BaseCommand;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.Database;
import unprotesting.com.github.data.ShopUtil;
import unprotesting.com.github.util.Format;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AutoTuneRemoveShopCommand extends BaseCommand {

    public AutoTuneRemoveShopCommand(@NotNull AutoTune plugin) {
        super(plugin, "removeshop", "autotune.admin", "Remove a shop.", "/autotune removeshop <shopName>");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (super.execute(sender, args)) {
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(getUsage());
            return true;
        }

        String shopName = args[0].toLowerCase();

        Database.acquireWriteLock();
        try {
            if (ShopUtil.removeShop(shopName)) {
                Format.sendMessage(sender, Config.get().getAdminShopRemoved());
            } else {
                Format.sendMessage(sender, Config.get().getAdminShopNotFound());
            }
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
        }
        return super.onTabComplete(sender, args);
    }
}
