package com.github.lye.events;

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
import com.github.lye.util.EconomyUtil;
import com.github.lye.util.Format;

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

    public static void runDeposit(Database database) {
        Database.acquireWriteLock();
        try {
            for (String s : ShopUtil.getShopNames(database)) {
                Shop shop = ShopUtil.getShop(database, s, true);
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
                    ShopUtil.addTransaction(database, new Transaction(
                            price, amount, entry.getKey(), s, TransactionType.SELL));
                    EconomyDataUtil.increaseEconomyData("GDP", total / 2);
                    double loss = shop.getPrice() * amount - total;
                    EconomyDataUtil.increaseEconomyData("LOSS", loss);
                    String balance = Format.currency(EconomyUtil.getEconomy().getBalance(player));

                    TagResolver resolver = TagResolver.resolver(
                            Placeholder.parsed("total", Format.currency(total)),
                            Placeholder.parsed("balance", balance));

                    if (player.isOnline()) {
                        Format.sendMessage(Objects.requireNonNull(player.getPlayer()),
                                Config.get().getAutosellProfit(), resolver);
                    }

                }

                shop.clearAutosell();
                ShopUtil.putShop(database, s, shop);
            }
        } finally {
            Database.releaseWriteLock();
        }
    }

}
