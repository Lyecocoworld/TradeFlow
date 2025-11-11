package unprotesting.com.github.commands;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import unprotesting.com.github.AutoTune;
import unprotesting.com.github.commands.core.BaseCommand;

public class MigrateDatabaseCommand extends BaseCommand {

    public MigrateDatabaseCommand(AutoTune plugin) {
        super(plugin, "at-migrate-database", "autotune.admin", "Migrates data from MapDB to MySQL.", "/at-migrate-database");
        setPlayerOnly(false);
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (super.execute(sender, args)) {
            return true;
        }

        sender.sendMessage("This feature is not yet implemented.");
        return true;
    }
}
