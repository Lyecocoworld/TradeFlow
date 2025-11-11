package com.github.lye.commands;

import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import com.github.lye.TradeFlow;
import com.github.lye.config.Config;
import com.github.lye.data.PurchaseUtil;
import com.github.lye.data.Shop;
import com.github.lye.data.ShopUtil;
import com.github.lye.util.EconomyUtil;
import com.github.lye.util.Format;
import com.github.lye.data.Transaction;
import com.github.lye.commands.core.BaseCommand; // Import BaseCommand

import java.util.ArrayList;
import java.util.List;

/**
 * The command for selling items.
 */
public class SellCommand extends BaseCommand { // Extend BaseCommand

    public SellCommand(@NotNull TradeFlow plugin) {
        super(plugin, "tfsell", "tradeflow.command.sell", "Open a panel to sell items.", "/tfsell");
        setPlayerOnly(true);
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        // BaseCommand handles playerOnly and permission checks
        if (super.execute(sender, args)) {
            return true;
        }

        Player player = (Player) sender; // Cast is safe due to playerOnly = true

        if (args.length > 0) {
            sender.sendMessage(getUsage());
            return true;
        }

        // Logic from the original interpret method
        player.getOpenInventory().close();
        Config config = Config.get();
        boolean isSellLimits = config.isEnableSellLimits();
        ChestGui gui = new ChestGui(5, Config.get().getGuiTitleSellPanel());

        gui.setOnClose(event -> {
            double totalProfit = 0.0;
            List<ItemStack> itemsToReturn = new ArrayList<>();

            for (ItemStack item : gui.getInventory().getContents()) {
                if (item == null || item.getType().isAir()) {
                    continue;
                }

                String itemName = item.getType().toString().toLowerCase();
                Shop shop = ShopUtil.getShop(plugin.getDatabase(), itemName, false);

                if (shop == null || shop.getSellPrice() <= 0) {
                    itemsToReturn.add(item);
                    continue;
                }

                int amountToSell = item.getAmount();
                int amountToReturn = 0;

                if (isSellLimits) {
                    int sellsLeft = ShopUtil.getSellsLeft(plugin.getDatabase(), player, itemName);
                    if (amountToSell > sellsLeft) {
                        amountToReturn = amountToSell - sellsLeft;
                        amountToSell = sellsLeft;
                    }
                }

                if (amountToReturn > 0) {
                    ItemStack returnStack = item.clone();
                    returnStack.setAmount(amountToReturn);
                    itemsToReturn.add(returnStack);
                }

                if (amountToSell > 0) {
                    double itemProfit = shop.getSellPrice() * amountToSell;
                    totalProfit += itemProfit;

                    shop.addSells(player.getUniqueId(), amountToSell);
                    
                    Transaction transaction = new Transaction(
                            shop.getSellPrice(), amountToSell, player.getUniqueId(), itemName, Transaction.TransactionType.SELL);
                    ShopUtil.addTransaction(plugin.getDatabase(), transaction);
                }
            }

            if (totalProfit > 0) {
                EconomyUtil.getEconomy().depositPlayer(player, totalProfit);
                TagResolver resolver = net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.parsed("total", Format.currency(totalProfit));
                Format.sendMessage(player, config.getSellSuccess(), resolver);
            }

            for (ItemStack stackToReturn : itemsToReturn) {
                PurchaseUtil.returnItem(player, stackToReturn);
            }
        });
        gui.show(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        // This command doesn't take arguments, so no tab completion needed.
        return super.onTabComplete(sender, args); // BaseCommand will return empty list
    }
}