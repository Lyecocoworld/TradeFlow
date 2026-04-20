package com.github.lye.commands;

import com.github.lye.TradeFlow;
import com.github.lye.data.CentralBankStockManager;
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
            CentralBankStockManager bankMgr = plugin.getServices().get(CentralBankStockManager.class);
            double current = bankMgr.getMonetaryReserve();
            
            if (amount > current) {
                bankMgr.addMoney(amount - current);
            } else {
                bankMgr.removeMoney(current - amount);
            }
            
            Format.sendRawMessage(sender, "<green>Central Bank internal reserve updated to: <white>" + Format.currency(bankMgr.getMonetaryReserve()));
            
        } catch (NumberFormatException e) {
            Format.sendRawMessage(sender, "<red>Invalid amount.");
        }

        return true;
    }
}
