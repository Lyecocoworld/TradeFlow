package com.github.lye.service;

import com.github.lye.data.Shop;
import org.bukkit.entity.Player;

public interface IPurchaseValidationService {
    boolean validatePurchase(Player player, Shop shop, int amount, boolean isBuy);
    boolean validateSellItemStack(Player player, Shop shop, int amount);
}
