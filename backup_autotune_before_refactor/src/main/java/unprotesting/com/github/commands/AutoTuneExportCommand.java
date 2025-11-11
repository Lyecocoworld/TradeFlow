package unprotesting.com.github.commands;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import unprotesting.com.github.AutoTune;
import unprotesting.com.github.commands.core.BaseCommand;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.config.TxtHandler;
import unprotesting.com.github.util.Format;

import java.util.List;

public class AutoTuneExportCommand extends BaseCommand {

    public AutoTuneExportCommand(@NotNull AutoTune plugin) {
        super(plugin, "export", "autotune.admin", "Export prices to a text file.", "/autotune export");
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

        TxtHandler.exportPrices();
        Format.sendMessage(sender, Config.get().getAdminPricesExported());
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return super.onTabComplete(sender, args);
    }
}
