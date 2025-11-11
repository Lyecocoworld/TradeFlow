package com.github.lye.commands;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import com.github.lye.TradeFlow;
import com.github.lye.commands.core.BaseCommand;

public class MigrateDatabaseCommand extends BaseCommand {

    public MigrateDatabaseCommand(TradeFlow plugin) {
        super(plugin, "tf-migrate-database", "tradeflow.admin", "Migrates data from MapDB to MySQL.", "/tf-migrate-database");
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
