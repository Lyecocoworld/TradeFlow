package com.github.lye.commands;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import com.github.lye.TradeFlow;
import com.github.lye.commands.core.BaseCommand;
import com.github.lye.config.Config;
import com.github.lye.data.CentralBankStockManager;
import com.github.lye.data.Database;
import com.github.lye.data.Loan;
import com.github.lye.service.IMessageService;
import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.config.settings.IMessageSettings;
import com.github.lye.util.EconomyUtil;
import com.github.lye.util.Format;
import com.github.lye.util.arguments.ArgumentParser;

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
        IMessageService messageService = plugin.getServices().get(IMessageService.class);
        IMessageSettings messageSettings = plugin.getServices().get(IMessageSettings.class);
        IPluginSettings pluginSettings = plugin.getServices().get(IPluginSettings.class);
        Database database = plugin.getServices().get(Database.class);

        if (args.length != 1) {
            messageService.sendErrorMessage(sender, getUsage(), null);
            return true;
        }

        Optional<Double> valueOptional = ArgumentParser.getDouble(sender, messageService, args[0], messageSettings.getLoanInvalidAmount());
        if (valueOptional.isEmpty()) {
            return true;
        }
        double value = valueOptional.get();

        if (value <= 0) {
            messageService.sendErrorMessage(player, messageSettings.getLoanInvalidAmount(), null);
            return true;
        }

        Database.acquireWriteLock();
        try {
            int activeLoanCount = 0;
            for (Loan existingLoan : database.getLoans().values()) {
                if (existingLoan.getPlayer().equals(player.getUniqueId()) && !existingLoan.isPaid()) {
                    activeLoanCount++;
                }
            }

            if (activeLoanCount >= pluginSettings.getMaxActiveLoans()) {
                TagResolver resolver = Placeholder.parsed("limit", String.valueOf(pluginSettings.getMaxActiveLoans()));
                messageService.sendErrorMessage(player, messageSettings.getLoanLimitReached(), resolver);
                return true;
            }

            CentralBankStockManager bankManager = plugin.getServices().get(CentralBankStockManager.class);
            if (bankManager == null || bankManager.getMonetaryReserve() < value) {
                messageService.sendErrorMessage(player,
                        "<red>The Central Bank does not have sufficient reserves to issue this loan.</red>", null);
                return true;
            }

            double base = value;
            double loanAmountWithInterest = value + value * pluginSettings.getLoanInterestMultiplier() * pluginSettings.getInterest();
            Loan loan = Loan.builder().player(player.getUniqueId()).value(loanAmountWithInterest).base(base).paid(false).build();
            String loanKey = java.util.UUID.randomUUID().toString();
            database.updateLoan(loanKey, loan);
            EconomyUtil.getEconomy().depositPlayer(player, base);
            EconomyUtil.transferFromCentralBank(base, plugin);
            messageService.sendInfoMessage(sender, "loan-taken-success", Placeholder.parsed("amount", Format.currency(base)));

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
