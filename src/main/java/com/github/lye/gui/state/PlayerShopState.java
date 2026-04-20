package com.github.lye.gui.state;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Getter
@Setter
public class PlayerShopState {

    public enum ShopScreen {
        MAIN,
        SECTION,
        PURCHASE,
        ENCHANT_LEVELS,
        PURCHASE_ENCHANT
    }

    private final UUID playerId;
    private ShopScreen screen;
    private String sectionName;
    private String itemName;
    private int enchantLevel;
    private int page;
    private int amount;

    public PlayerShopState(UUID playerId) {
        this.playerId = playerId;
        this.reset();
    }

    public void goToMain() {
        this.screen = ShopScreen.MAIN;
        this.sectionName = null;
        this.itemName = null;
        this.enchantLevel = 0;
        this.page = 0;
        this.amount = 1;
    }

    public void reset() {
        goToMain();
    }

    public void goToSection(String sectionName) {
        this.screen = ShopScreen.SECTION;
        this.sectionName = sectionName;
        this.itemName = null;
        this.enchantLevel = 0;
        this.page = 0;
        this.amount = 1;
    }

    public void goToPurchase(String itemName) {
        this.screen = ShopScreen.PURCHASE;
        // sectionName is preserved
        this.itemName = itemName;
        this.enchantLevel = 0;
        // page is preserved
        this.amount = 1;
    }

    public void goToEnchantLevels(String itemName) {
        this.screen = ShopScreen.ENCHANT_LEVELS;
        this.itemName = itemName;
        this.enchantLevel = 0;
        this.amount = 1;
    }

    public void goToPurchaseEnchant(String itemName, int level) {
        this.screen = ShopScreen.PURCHASE_ENCHANT;
        this.itemName = itemName;
        this.enchantLevel = level;
        this.amount = 1;
    }

    public void setPage(int page) {
        this.page = Math.max(0, page);
    }

    public void setAmount(int amount) {
        this.amount = Math.max(1, amount);
    }
}
