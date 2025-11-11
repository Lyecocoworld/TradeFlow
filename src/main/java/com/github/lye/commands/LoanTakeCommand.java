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
import com.github.lye.util.EconomyUtil;
import com.github.lye.util.Format;
import com.github.lye.util.arguments.ArgumentParser; // Import ArgumentParser

import java.util.List;
import java.util.Optional;

public class LoanTakeCommand extends BaseCommand {

    public LoanTakeCommand(@NotNull TradeFlow plugin) {
        super(plugin, "take", "tradeflow.command.loan.take", "Take a new loan.", "/loan take <amount>");
        setPlayerOnly(true);
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (super.execute(sender, args)) {
            return true;
        }

        Player player = (Player) sender;
        Config config = Config.get();

        if (args.length != 1) {
            Format.sendMessage(sender, getUsage());
            return true;
        }

        Optional<Double> valueOptional = ArgumentParser.getDouble(sender, args[0], config.getLoanInvalidAmount());
        if (valueOptional.isEmpty()) {
            return true;
        }
        double value = valueOptional.get();

        if (value <= 0) {
            Format.sendMessage(player, config.getLoanInvalidAmount());
            return true;
        }

        Database.acquireWriteLock();
        try {
            int activeLoanCount = 0;
            for (Loan existingLoan : Database.get().getLoans().values()) {
                if (existingLoan.getPlayer().equals(player.getUniqueId()) && !existingLoan.isPaid()) {
                    activeLoanCount++;
                }
            }

            if (activeLoanCount >= config.getMaxActiveLoans()) {
                TagResolver resolver = Placeholder.parsed("limit", String.valueOf(config.getMaxActiveLoans()));
                Format.sendMessage(player, config.getLoanLimitReached(), resolver);
                return true;
            }

            if (EconomyUtil.getEconomy().getBalance(player)
                    <= value + value * 0.05 * config.getInterest()) { // This calculation seems off, should be interest on the loan amount
                Format.sendMessage(player, config.getLoanNotEnoughMoneyLoan());
                return true;
            }

            double base = value;
            double loanAmountWithInterest = value + value * 0.01 * config.getInterest();
            Loan loan = Loan.builder().player(player.getUniqueId()).value(loanAmountWithInterest).base(base).paid(false).build();
            Database.get().getLoans().put(java.util.UUID.randomUUID().toString(), loan);
            EconomyUtil.getEconomy().depositPlayer(player, base);
            Format.sendMessage(sender, "loan-taken-success", Placeholder.parsed("amount", Format.currency(base)));

        } finally {
            Database.releaseWriteLock();
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        // Potentially suggest common loan amounts
        return super.onTabComplete(sender, args);
    }
}