package unprotesting.com.github.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import unprotesting.com.github.AutoTune;
import unprotesting.com.github.commands.core.BaseCommand;
import unprotesting.com.github.config.Config;

import unprotesting.com.github.util.Format;

import java.util.List;

public class AutoTuneUpdateCommand extends BaseCommand {

    public AutoTuneUpdateCommand(@NotNull AutoTune plugin) {
        super(plugin, "update", "autotune.admin", "Update prices.", "/autotune update");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (super.execute(sender, args)) {
            return true;
        }

        if (args.length > 0) {
            Format.sendMessage(sender, getUsage());
            return true;
        }

        if (sender instanceof Player) {
            Player player = (Player) sender;
            Format.sendMessage(player, Config.get().getAdminPricesUpdating());
            plugin.recalculatePrices();
            Format.sendMessage(player, Config.get().getAdminPricesUpdated());
        } else {
            Format.sendMessage(sender, Config.get().getPlayersOnly()); // Use message key
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return super.onTabComplete(sender, args);
    }
}