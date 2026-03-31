package com.github.lye.config.settings;

import java.util.List;

public interface IMessageSettings {
    String getNotInShop();
    String getNotEnoughMoney();
    String getNotEnoughSpace();
    String getNotEnoughItems();
    String getRunOutOfBuys();
    String getRunOutOfSells();
    String getShopPurchase();
    String getShopSell();
    String getHoldItemInHand();
    String getEnchantmentError();
    String getAutosellProfit();
    String getInvalidShopSection();
    String getBackgroundPaneText();
    String getGuiTitleShop();
    String getPermissionDenied();
    String getAdminReloadingShops();
    String getAdminShopsReloaded();
    String getAdminPricesExported();
    String getAdminPricesImported();
    String getAdminShopRemoved();
    String getAdminShopNotFound();
    String getAdminInvalidPrice();
    String getAdminPriceSet();
    String getAdminPricesUpdating();
    String getAdminPricesUpdated();
    String getGuiBackToMenu();
    String getGuiGoToPage();
    String getPlayersOnly();
    String getAutosellNoEnchanted();
    String getLoanUsage();
    String getLoanPaidBack();
    String getLoanNotEnoughMoneyPayback();
    String getLoanInvalidAmount();
    String getLoanLimitReached();
    String getLoanNotEnoughMoneyLoan();
    String getLoanInfo();
    String getSellSuccess();
    String getGuiTitleSellPanel();
    String getMarketOpeningGui();
    String getAutotradeInvalidShopName();
    String getAutotradeDisabled();
    String getAutotradeEnabled();
    String getAutotradeToggled();
    String getLoanTakenSuccess();
    String getPlayerNotFound();
    String getAdminMigrationUsage();
    String getAdminMigrationMysqlRequired();
    String getAdminMigrationStarted();
    String getAdminMigrationNoFile();
    String getAdminMigrationNoData();
    String getAdminMigrationComplete();
    String getAdminMigrationError();

    List<String> getShopLore();
    List<String> getShopGdpLore();
    List<String> getPurchaseBuyLore();
    List<String> getPurchaseEnchantLore();
    List<String> getPurchaseSellLore();
    List<String> getAutosellLore();
    List<String> getHelp();
    List<String> getAdminHelp();
    List<String> getTutorial();

    String getMessage(String key); // For direct access to messages.yml
}
