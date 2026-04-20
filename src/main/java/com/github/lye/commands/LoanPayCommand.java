package com.github.lye.commands;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import com.github.lye.TradeFlow;
import com.github.lye.commands.core.BaseCommand;
import com.github.lye.service.IMessageService;
import com.github.lye.config.settings.IMessageSettings;
import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.config.Config;
import com.github.lye.data.Database;
import com.github.lye.data.EconomyDataUtil;
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
            Database db = plugin.getServices().get(Database.class);
            IMessageService msgService = plugin.getServices().get(IMessageService.class);
            IMessageSettings msgSettings = plugin.getServices().get(IMessageSettings.class);
            for (Map.Entry<String, Loan> entry : db.getLoans().entrySet()) {
                Loan loan = entry.getValue();
                if (loan.getPlayer().equals(player.getUniqueId())) {
                    if (loan.isPaid()) {
                        continue;
                    }

                    if (loan.payBack(plugin.getServices().get(EconomyDataUtil.class), plugin.getServices().get(IPluginSettings.class), plugin)) {
                        TagResolver resolver = Placeholder.parsed("value", Format.currency(loan.getValue()));
                        msgService.sendInfoMessage(player, msgSettings.getLoanPaidBack(), resolver);
                    } else {
                        msgService.sendErrorMessage(player, msgSettings.getLoanNotEnoughMoneyPayback(), null);
                    }
                    db.updateLoan(entry.getKey(), loan);
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
