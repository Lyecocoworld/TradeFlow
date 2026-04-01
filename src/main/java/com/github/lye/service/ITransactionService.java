package com.github.lye.service;

import com.github.lye.data.Shop;
import com.github.lye.data.Transaction;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface ITransactionService {
    void recordTransaction(Player player, Shop shop, int amount, double total, boolean isBuy);
    void recordSellTransaction(UUID uuid, String itemName, Shop itemShop, int amount, double total, double price);
}
