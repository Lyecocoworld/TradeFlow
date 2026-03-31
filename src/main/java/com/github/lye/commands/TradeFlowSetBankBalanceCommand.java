package com.github.lye.commands;

import com.github.lye.TradeFlow;
import com.github.lye.commands.core.BaseCommand;
import com.github.lye.util.EconomyUtil;
import com.github.lye.util.Format;
import org.bukkit.command.CommandSender;

public class TradeFlowSetBankBalanceCommand extends BaseCommand {

    public TradeFlowSetBankBalanceCommand(TradeFlow plugin) {
        super(plugin, "setbankbalance", "tradeflow.admin", "Force the Central Bank balance.", "/tfadmin setbankbalance <amount>");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("Usage: " + getUsage());
            return true;
        }

        try {
            double amount = Double.parseDouble(args[0]);
            double current = plugin.getCentralBankStockManager().getMonetaryReserve();
            
            if (amount > current) {
                plugin.getCentralBankStockManager().addMoney(amount - current);
            } else {
                plugin.getCentralBankStockManager().removeMoney(current - amount);
            }
            
            sender.sendMessage("§aCentral Bank internal reserve updated to: §f" + Format.currency(plugin.getCentralBankStockManager().getMonetaryReserve()));
            
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid amount.");
        }

        return true;
    }
}
