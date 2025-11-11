package unprotesting.com.github.commands;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import unprotesting.com.github.AutoTune;
import unprotesting.com.github.commands.core.BaseCommand;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.Database;
import unprotesting.com.github.data.ShopUtil;
import unprotesting.com.github.util.Format;

import java.util.List;

public class AutoTuneReloadCommand extends BaseCommand {

    public AutoTuneReloadCommand(@NotNull AutoTune plugin) {
        super(plugin, "reload", "autotune.admin", "Reload shops configuration.", "/autotune reload");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (super.execute(sender, args)) {
            return true;
        }

        if (args.length > 0) {
            sender.sendMessage(getUsage());
            return true;
        }

        Config config = Config.get();
        Format.sendMessage(sender, config.getAdminReloadingShops());
        Database.acquireWriteLock();
        try {
            ShopUtil.reload();
        } finally {
            Database.releaseWriteLock();
        }
        Format.sendMessage(sender, config.getAdminShopsReloaded());
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return super.onTabComplete(sender, args);
    }
}
