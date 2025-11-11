package unprotesting.com.github.commands;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import unprotesting.com.github.AutoTune;
import unprotesting.com.github.commands.core.BaseCommand;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.util.Format;

import java.util.List;

public class AutoTuneAdminHelpCommand extends BaseCommand {

    public AutoTuneAdminHelpCommand(@NotNull AutoTune plugin) {
        super(plugin, "adminhelp", "autotune.admin", "Display AutoTune admin help messages.", "/autotune adminhelp");
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

        Config config = Config.get();
        for (String message : config.getAdminHelp()) {
            Format.sendMessage(sender, message);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return super.onTabComplete(sender, args);
    }
}
