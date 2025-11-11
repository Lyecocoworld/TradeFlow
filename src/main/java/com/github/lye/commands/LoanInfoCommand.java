package com.github.lye.commands;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import com.github.lye.TradeFlow;
import com.github.lye.commands.core.BaseCommand;
import com.github.lye.config.Config;
import com.github.lye.data.Database;
import com.github.lye.data.Loan;
import com.github.lye.util.Format;

import java.util.List;
import java.util.UUID;

public class LoanInfoCommand extends BaseCommand {

    public LoanInfoCommand(@NotNull TradeFlow plugin) {
        super(plugin, "info", "tradeflow.command.loan.info", "Display your current loans.", "/loan info");
        setPlayerOnly(true);
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (super.execute(sender, args)) {
            return true;
        }

        Player player = (Player) sender;
        getTotalLoans(player);
        return true;
    }

    private void getTotalLoans(@NotNull Player player) {
        Database.acquireReadLock();
        try {
            UUID uuid = player.getUniqueId();
            double total = 0;

            for (Loan loan : Database.get().getLoans().values()) {
                if (loan.getPlayer().equals(uuid)) {
                    if (loan.isPaid()) {
                        continue;
                    }
                    total += loan.getValue();
                }
            }
            TagResolver resolver = Placeholder.parsed("total", Format.currency(total));
            Format.sendMessage(player, Config.get().getLoanInfo(), resolver);
        } finally {
            Database.releaseReadLock();
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return super.onTabComplete(sender, args);
    }
}
