package unprotesting.com.github.commands;

import org.bukkit.command.CommandSender;
import unprotesting.com.github.AutoTune;
import unprotesting.com.github.commands.core.BaseCommand;

import java.util.List;

public class AutoTuneCommand extends BaseCommand {
    public AutoTuneCommand(AutoTune plugin) {
        super(plugin, "at", "autotune.admin", "AutoTune main command.", "/at <subcommand>");
        registerSubCommand(new AutoTuneHelpCommand(plugin));
        registerSubCommand(new AutoTuneAdminHelpCommand(plugin));
        registerSubCommand(new AutoTuneReloadCommand(plugin));
        registerSubCommand(new AutoTuneUpdateCommand(plugin));
        registerSubCommand(new AutoTuneExportCommand(plugin));
        registerSubCommand(new AutoTuneImportCommand(plugin));
        registerSubCommand(new AutoTuneRemoveShopCommand(plugin));
        registerSubCommand(new AutoTuneSetPriceCommand(plugin));
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // This will be the main logic for the /autotune command, handling subcommands.
        // For now, BaseCommand's default execute will handle showing usage or dispatching to subcommands.
        return super.execute(sender, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        // This will handle tab completion for /autotune subcommands.
        return super.onTabComplete(sender, args);
    }
}