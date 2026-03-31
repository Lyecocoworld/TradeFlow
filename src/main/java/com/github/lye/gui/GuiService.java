package com.github.lye.gui;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import com.github.lye.data.Section;

public interface GuiService {

    void openMainShop(@NotNull Player player);

    void openSection(@NotNull Player player, @NotNull Section section, @NotNull String sectionName);

    void openPurchase(@NotNull Player player, @NotNull String shopName);

    void openEnchantLevels(@NotNull Player player, @NotNull String enchantShopName);

    void openPurchaseEnchant(@NotNull Player player, @NotNull String enchantShopName, int level);

    void openSellPanel(@NotNull Player player);
}

