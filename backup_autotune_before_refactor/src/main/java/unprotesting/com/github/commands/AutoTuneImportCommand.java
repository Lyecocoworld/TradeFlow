package unprotesting.com.github.commands;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import unprotesting.com.github.AutoTune;
import unprotesting.com.github.commands.core.BaseCommand;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.config.TxtHandler;
import unprotesting.com.github.util.Format;

import java.util.List;

public class AutoTuneImportCommand extends BaseCommand {

    public AutoTuneImportCommand(@NotNull AutoTune plugin) {
        super(plugin, "import", "autotune.admin", "Import prices from a text file.", "/autotune import");
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

        TxtHandler.importPrices();
        Format.sendMessage(sender, Config.get().getAdminPricesImported());
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return super.onTabComplete(sender, args);
    }
}
