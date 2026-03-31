package com.github.lye.events;

import com.github.lye.TradeFlow;
import com.github.lye.data.Database;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import com.github.lye.config.Config;
import com.github.lye.data.EconomyDataUtil;
import com.github.lye.data.Shop;
import com.github.lye.data.ShopUtil;
import com.github.lye.data.Transaction;
import com.github.lye.data.Transaction.TransactionType;
import com.github.lye.service.IMessageService;
import com.github.lye.config.settings.IMessageSettings;
import com.github.lye.util.EconomyUtil;
import com.github.lye.util.Format;
import com.github.lye.events.TradeFlowEvent;

/**
 * The event for sending a player their money from items they have auto-sold.
 */
public class AutosellProfitEvent extends TradeFlowEvent {

    /**
     * Updates the autosell profit.
     *
     * @param isAsync Whether the event is being run async or not.
     */
    public AutosellProfitEvent(boolean isAsync) {
        super(isAsync);
    }

    public static void runDeposit(Database database, ShopUtil shopUtil, EconomyDataUtil economyDataUtil,
                                  IMessageService messageService, IMessageSettings messageSettings, TradeFlow plugin) {
        Database.acquireWriteLock();
        try {
            for (String s : shopUtil.getShopNames()) {
                Shop shop = shopUtil.getShop(s, true);
                Map<UUID, Integer> autosell = shop.getAutosell();

                if (autosell.isEmpty()) {
                    continue;
                }

                for (Map.Entry<UUID, Integer> entry : autosell.entrySet()) {

                    if (entry.getValue() <= 0) {
                        continue;
                    }

                    int amount = entry.getValue();
                    double price = shop.getSellPrice();
                    double total = price * amount;
                    OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
                    EconomyUtil.getEconomy().depositPlayer(player, total);
                    EconomyUtil.transferFromCentralBank(total, plugin);
                    shopUtil.addTransaction(new Transaction(
                            price, amount, entry.getKey(), s, TransactionType.SELL));
                    economyDataUtil.increaseEconomyData("GDP", total / 2);
                    double loss = shop.getPrice() * amount - total;
                    economyDataUtil.increaseEconomyData("LOSS", loss);
                    String balance = Format.currency(EconomyUtil.getEconomy().getBalance(player));

                    TagResolver resolver = TagResolver.resolver(
                            Placeholder.parsed("total", Format.currency(total)),
                            Placeholder.parsed("balance", balance));

                    if (player.isOnline()) {
                        messageService.sendInfoMessage(
                                Objects.requireNonNull(player.getPlayer()),
                                messageSettings.getAutosellProfit(),
                                resolver);
                    }

                }

                shop.clearAutosell();
                shopUtil.putShop(s, shop);
            }
        } finally {
            Database.releaseWriteLock();
        }
    }

}
