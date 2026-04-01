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
import java.util.Map;

public class LoanPayCommand extends BaseCommand {

    public LoanPayCommand(@NotNull TradeFlow plugin) {
        super(plugin, "pay", "tradeflow.command.loan.pay", "Pay back your loans.", "/loan pay");
        setPlayerOnly(true);
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (super.execute(sender, args)) {
            return true;
        }

        Player player = (Player) sender;

        Database.acquireWriteLock();
        try {
            for (Map.Entry<String, Loan> entry : plugin.getDatabase().getLoans().entrySet()) {
                Loan loan = entry.getValue();
                if (loan.getPlayer().equals(player.getUniqueId())) {
                    if (loan.isPaid()) {
                        continue;
                    }

                    if (loan.payBack(plugin.getEconomyDataUtil(), plugin.getPluginSettings(), plugin)) {
                        TagResolver resolver = Placeholder.parsed("value", Format.currency(loan.getValue()));
                        plugin.getMessageService().sendInfoMessage(player, plugin.getMessageSettings().getLoanPaidBack(), resolver);
                    } else {
                        plugin.getMessageService().sendErrorMessage(player, plugin.getMessageSettings().getLoanNotEnoughMoneyPayback(), null);
                    }
                    plugin.getDatabase().updateLoan(entry.getKey(), loan);
                }
            }
        } finally {
            Database.releaseWriteLock();
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return super.onTabComplete(sender, args);
    }
}
