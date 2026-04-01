package com.github.lye.service.impl;

import com.github.lye.TradeFlow;
import com.github.lye.data.Database;
import com.github.lye.data.EconomyDataUtil;
import com.github.lye.data.CentralBankStockManager;
import com.github.lye.data.Shop;
import com.github.lye.data.ShopUtil;
import com.github.lye.data.Transaction;
import com.github.lye.data.Transaction.TransactionType;
import com.github.lye.gmq.GmqService;
import com.github.lye.redis.RedisClient;
import com.github.lye.service.ITransactionService;
import org.bukkit.entity.Player;

import java.util.UUID;

public class DefaultTransactionService implements ITransactionService {

    private final TradeFlow plugin;
    private final Database database;
    private final EconomyDataUtil economyDataUtil;
    private final ShopUtil shopUtil;
    private final CentralBankStockManager CentralBankStockManager;
    private final GmqService gmqService;
    private final RedisClient redisClient;
    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(DefaultTransactionService.class.getName());

    public DefaultTransactionService(TradeFlow plugin,
                                     Database database,
                                     EconomyDataUtil economyDataUtil,
                                     ShopUtil shopUtil,
                                     CentralBankStockManager CentralBankStockManager,
                                     GmqService gmqService,
                                     RedisClient redisClient) {
        this.plugin = plugin;
        this.database = database;
        this.economyDataUtil = economyDataUtil;
        this.shopUtil = shopUtil;
        this.CentralBankStockManager = CentralBankStockManager;
        this.gmqService = gmqService;
        this.redisClient = redisClient;
    }

    @Override
    public void recordTransaction(Player player, Shop shop, int amount, double total, boolean isBuy) {
        TransactionType position = isBuy ? TransactionType.BUY : TransactionType.SELL;
        Transaction transaction = new Transaction(shop.getPrice(), amount, player.getUniqueId(), shop.getName(), position);
        database.putTransaction(java.util.UUID.randomUUID().toString(), transaction);
        economyDataUtil.increaseEconomyData("GDP", total / 2);

        // Vault withdraw/deposit and CentralBank transfers are already handled
        // by PurchaseUtil.processTransaction() — do NOT duplicate here (fixes C1).
        if (isBuy) {
            shop.addBuys(player.getUniqueId(), amount);

            // Decrease Physical Stock
            if (shop.getMinBaseStock() > 0) {
                shop.setCurrentStock(Math.max(0, shop.getCurrentStock() - amount));
            }
        } else {
            shop.addSells(player.getUniqueId(), amount);

            // Increase Physical Stock
            if (shop.getMinBaseStock() > 0) {
                shop.setCurrentStock(shop.getCurrentStock() + amount);
            }
        }

        double loss = shop.getPrice() * amount - total;
        economyDataUtil.increaseEconomyData("LOSS", loss);

        // Stock recording is handled by TradeExecutionService (orchestrator) —
        // do NOT record stock here to avoid double-counting (fixes E5).
        if (!isBuy) {
            if (gmqService != null) {
                gmqService.onItemSold(shop.getName(), amount);
            }
            // Publish stock update to Redis (Phase 2)
            if (redisClient != null && redisClient.isEnabled()) {
                String payload = "{\"item\":\"" + shop.getName() + "\",\"delta\":" + amount + "}";
                LOGGER.fine("[Redis] Publishing stock update: " + payload);
                redisClient.publish("tradeflow:stock-updates", payload);
            }
        } else {
            if (gmqService != null) {
                gmqService.onItemBought(shop.getName(), amount);
            }
        }

        shopUtil.putShop(shop.getName(), shop);
        
        // Trigger price update
        plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> plugin.recalculatePrices());
    }

    @Override
    public void recordSellTransaction(UUID uuid, String itemName, Shop itemShop, int amount, double total, double price) {
        Transaction transaction = new Transaction(
                price, amount, uuid, itemName, TransactionType.SELL);
        database.putTransaction(java.util.UUID.randomUUID().toString(), transaction);
        economyDataUtil.increaseEconomyData("GDP", total / 2);
        double loss = itemShop.getPrice() - itemShop.getSellPrice();
        economyDataUtil.increaseEconomyData("LOSS", loss * amount);
        itemShop.addSells(uuid, amount);
        
        // Increase Physical Stock
        if (itemShop.getMinBaseStock() > 0) {
            itemShop.setCurrentStock(itemShop.getCurrentStock() + amount);
        }
        
        shopUtil.putShop(itemName, itemShop);

        // Trigger price update
        plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> plugin.recalculatePrices());
    }
}
