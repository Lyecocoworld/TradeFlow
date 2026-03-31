package com.github.lye.config.settings.impl;

import com.github.lye.config.settings.IMessageSettings;
import com.github.lye.util.Format;
import com.github.lye.util.TradeFlowLogger;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Arrays;
import java.util.List;

public class DefaultMessageSettings implements IMessageSettings {

    private final YamlConfiguration messagesYml;
    private final TradeFlowLogger logger;

    private final String notInShop;
    private final String notEnoughMoney;
    private final String notEnoughSpace;
    private final String notEnoughItems;
    private final String runOutOfBuys;
    private final String runOutOfSells;
    private final String shopPurchase;
    private final String shopSell;
    private final String holdItemInHand;
    private final String enchantmentError;
    private final String autosellProfit;
    private final String invalidShopSection;
    private final String backgroundPaneText;
    private final String guiTitleShop;
    private final String permissionDenied;
    private final String adminReloadingShops;
    private final String adminShopsReloaded;
    private final String adminPricesExported;
    private final String adminPricesImported;
    private final String adminShopRemoved;
    private final String adminShopNotFound;
    private final String adminInvalidPrice;
    private final String adminPriceSet;
    private final String adminPricesUpdating;
    private final String adminPricesUpdated;
    private final String guiBackToMenu;
    private final String guiGoToPage;
    private final String playersOnly;
    private final String autosellNoEnchanted;
    private final String loanUsage;
    private final String loanPaidBack;
    private final String loanNotEnoughMoneyPayback;
    private final String loanInvalidAmount;
    private final String loanLimitReached;
    private final String loanNotEnoughMoneyLoan;
    private final String loanInfo;
    private final String sellSuccess;
    private final String guiTitleSellPanel;
    private final String marketOpeningGui;
    private final String autotradeInvalidShopName;
    private final String autotradeDisabled;
    private final String autotradeEnabled;
    private final String autotradeToggled;
    private final String loanTakenSuccess;
    private final String playerNotFound;
    private final String adminMigrationUsage;
    private final String adminMigrationMysqlRequired;
    private final String adminMigrationStarted;
    private final String adminMigrationNoFile;
    private final String adminMigrationNoData;
    private final String adminMigrationComplete;
    private final String adminMigrationError;

    private final List<String> shopLore;
    private final List<String> shopGdpLore;
    private final List<String> purchaseBuyLore;
    private final List<String> purchaseEnchantLore;
    private final List<String> purchaseSellLore;
    private final List<String> autosellLore;
    private final List<String> help;
    private final List<String> adminHelp;
    private final List<String> tutorial;

    public DefaultMessageSettings(YamlConfiguration messagesYml) {
        this.messagesYml = messagesYml;
        this.logger = Format.getLog();

        this.notInShop = messagesYml.getString("not-in-shop");
        logger.finest("Not in shop: " + notInShop);
        this.notEnoughMoney = messagesYml.getString("not-enough-money");
        logger.finest("Not enough money: " + notEnoughMoney);
        this.notEnoughSpace = messagesYml.getString("not-enough-space");
        logger.finest("Not enough space: " + notEnoughSpace);
        this.notEnoughItems = messagesYml.getString("not-enough-items");
        logger.finest("Not enough items: " + notEnoughItems);

        this.runOutOfBuys = messagesYml.getString("run-out-of-buys");
        logger.finest("Run out of buys: " + runOutOfBuys);
        this.runOutOfSells = messagesYml.getString("run-out-of-sells");
        logger.finest("Run out of sells: " + runOutOfSells);
        this.shopPurchase = messagesYml.getString("shop-purchase");
        logger.finest("Shop purchase: " + shopPurchase);
        this.shopSell = messagesYml.getString("shop-sell");
        logger.finest("Shop sell: " + shopSell);
        this.holdItemInHand = messagesYml.getString("hold-item-in-hand");
        logger.finest("Hold item in hand: " + holdItemInHand);
        this.enchantmentError = messagesYml.getString("enchantment-error");
        logger.finest("Enchantment error: " + enchantmentError);
        this.autosellProfit = messagesYml.getString("autosell-profit");
        logger.finest("Autosell profit: " + autosellProfit);
        this.invalidShopSection = messagesYml.getString("invalid-shop-section");
        logger.finest("Invalid shop section: " + invalidShopSection);
        this.backgroundPaneText = messagesYml.getString("background-pane-text", "<obf>|</obf>");
        logger.finest("Background pane text: " + backgroundPaneText);

        this.guiTitleShop = messagesYml.getString("gui-title-shop");
        logger.finest("GUI Title Shop: " + guiTitleShop);
        this.permissionDenied = messagesYml.getString("permission-denied");
        logger.finest("Permission Denied: " + permissionDenied);
        this.adminReloadingShops = messagesYml.getString("admin-reloading-shops");
        logger.finest("Admin Reloading Shops: " + adminReloadingShops);
        this.adminShopsReloaded = messagesYml.getString("admin-shops-reloaded");
        logger.finest("Admin Shops Reloaded: " + adminShopsReloaded);
        this.adminPricesExported = messagesYml.getString("admin-prices-exported");
        logger.finest("Admin Prices Exported: " + adminPricesExported);
        this.adminPricesImported = messagesYml.getString("admin-prices-imported");
        logger.finest("Admin Prices Imported: " + adminPricesImported);
        this.adminShopRemoved = messagesYml.getString("admin-shop-removed");
        logger.finest("Admin Shop Removed: " + adminShopRemoved);
        this.adminShopNotFound = messagesYml.getString("admin-shop-not-found");
        logger.finest("Admin Shop Not Found: " + adminShopNotFound);
        this.adminInvalidPrice = messagesYml.getString("admin-invalid-price");
        logger.finest("Admin Invalid Price: " + adminInvalidPrice);
        this.adminPriceSet = messagesYml.getString("admin-price-set");
        logger.finest("Admin Price Set: " + adminPriceSet);
        this.adminPricesUpdating = messagesYml.getString("admin-prices-updating");
        logger.finest("Admin Prices Updating: " + adminPricesUpdating);
        this.adminPricesUpdated = messagesYml.getString("admin-prices-updated");
        logger.finest("Admin Prices Updated: " + adminPricesUpdated);

        this.guiBackToMenu = messagesYml.getString("gui-back-to-menu");
        logger.finest("GUI Back to Menu: " + guiBackToMenu);
        this.guiGoToPage = messagesYml.getString("gui-go-to-page");
        logger.finest("GUI Go to Page: " + guiGoToPage);

        this.playersOnly = messagesYml.getString("players-only");
        logger.finest("Players Only: " + playersOnly);
        this.autosellNoEnchanted = messagesYml.getString("autosell-no-enchanted");
        logger.finest("Autosell No Enchanted: " + autosellNoEnchanted);
        this.loanUsage = messagesYml.getString("loan-usage");
        logger.finest("Loan Usage: " + loanUsage);
        this.loanPaidBack = messagesYml.getString("loan-paid-back");
        logger.finest("Loan Paid Back: " + loanPaidBack);
        this.loanNotEnoughMoneyPayback = messagesYml.getString("loan-not-enough-money-payback");
        logger.finest("Loan Not Enough Money Payback: " + loanNotEnoughMoneyPayback);
        this.loanInvalidAmount = messagesYml.getString("loan-invalid-amount");
        logger.finest("Loan Invalid Amount: " + loanInvalidAmount);
        this.loanLimitReached = messagesYml.getString("loan-limit-reached");
        logger.finest("Loan Limit Reached: " + loanLimitReached);
        this.loanNotEnoughMoneyLoan = messagesYml.getString("loan-not-enough-money-loan");
        logger.finest("Loan Not Enough Money Loan: " + loanNotEnoughMoneyLoan);
        this.loanInfo = messagesYml.getString("loan-info");
        logger.finest("Loan Info: " + loanInfo);
        this.sellSuccess = messagesYml.getString("sell-success");
        logger.finest("Sell Success: " + sellSuccess);
        this.guiTitleSellPanel = messagesYml.getString("gui-title-sell-panel");
        logger.finest("GUI Title Sell Panel: " + guiTitleSellPanel);

        this.marketOpeningGui = messagesYml.getString("market-opening-gui");
        logger.finest("Market Opening GUI: " + marketOpeningGui);
        this.autotradeInvalidShopName = messagesYml.getString("autotrade-invalid-shop-name");
        logger.finest("Autotrade Invalid Shop Name: " + autotradeInvalidShopName);
        this.autotradeDisabled = messagesYml.getString("autotrade-disabled");
        logger.finest("Autotrade Disabled: " + autotradeDisabled);
        this.autotradeEnabled = messagesYml.getString("autotrade-enabled");
        logger.finest("Autotrade Enabled: " + autotradeEnabled);
        this.autotradeToggled = messagesYml.getString("autotrade-toggled");
        logger.finest("Autotrade Toggled: " + autotradeToggled);
        this.loanTakenSuccess = messagesYml.getString("loan-taken-success");
        logger.finest("Loan Taken Success: " + loanTakenSuccess);
        this.playerNotFound = messagesYml.getString("player-not-found");
        logger.finest("Player Not Found: " + playerNotFound);

        this.adminMigrationUsage = messagesYml.getString("admin-migration-usage");
        logger.finest("Admin Migration Usage: " + adminMigrationUsage);
        this.adminMigrationMysqlRequired = messagesYml.getString("admin-migration-mysql-required");
        logger.finest("Admin Migration MySQL Required: " + adminMigrationMysqlRequired);
        this.adminMigrationStarted = messagesYml.getString("admin-migration-started");
        logger.finest("Admin Migration Started: " + adminMigrationStarted);
        this.adminMigrationNoFile = messagesYml.getString("admin-migration-no-file");
        logger.finest("Admin Migration No File: " + adminMigrationNoFile);
        this.adminMigrationNoData = messagesYml.getString("admin-migration-no-data");
        logger.finest("Admin Migration No Data: " + adminMigrationNoData);
        this.adminMigrationComplete = messagesYml.getString("admin-migration-complete");
        logger.finest("Admin Migration Complete: " + adminMigrationComplete);
        this.adminMigrationError = messagesYml.getString("admin-migration-error");
        logger.finest("Admin Migration Error: " + adminMigrationError);

        this.shopLore = messagesYml.getStringList("shop-lore");
        logger.finest("Shop lore: " + Arrays.toString(shopLore.toArray()));
        this.shopGdpLore = messagesYml.getStringList("shop-gdp-lore");
        logger.finest("GDP shop lore: " + Arrays.toString(shopGdpLore.toArray()));
        this.purchaseBuyLore = messagesYml.getStringList("purchase-buy-lore");
        logger.finest("Purchase buy lore: " + Arrays.toString(purchaseBuyLore.toArray()));
        this.purchaseEnchantLore = messagesYml.getStringList("purchase-enchant-lore");
        logger.finest("Purchase enchant lore: " + Arrays.toString(purchaseEnchantLore.toArray()));
        this.purchaseSellLore = messagesYml.getStringList("purchase-sell-lore");
        logger.finest("Purchase sell lore: " + Arrays.toString(purchaseSellLore.toArray()));
        this.autosellLore = messagesYml.getStringList("autosell-lore");
        logger.finest("Autosell lore: " + Arrays.toString(autosellLore.toArray()));
        this.help = messagesYml.getStringList("help");
        logger.finest("Help: " + Arrays.toString(help.toArray()));
        this.adminHelp = messagesYml.getStringList("admin-help");
        logger.finest("AdminHelp: " + Arrays.toString(adminHelp.toArray()));
        this.tutorial = messagesYml.getStringList("tutorial");
        logger.finest("Tutorial: " + Arrays.toString(tutorial.toArray()));
    }

    @Override
    public String getNotInShop() { return notInShop; }
    @Override
    public String getNotEnoughMoney() { return notEnoughMoney; }
    @Override
    public String getNotEnoughSpace() { return notEnoughSpace; }
    @Override
    public String getNotEnoughItems() { return notEnoughItems; }
    @Override
    public String getRunOutOfBuys() { return runOutOfBuys; }
    @Override
    public String getRunOutOfSells() { return runOutOfSells; }
    @Override
    public String getShopPurchase() { return shopPurchase; }
    @Override
    public String getShopSell() { return shopSell; }
    @Override
    public String getHoldItemInHand() { return holdItemInHand; }
    @Override
    public String getEnchantmentError() { return enchantmentError; }
    @Override
    public String getAutosellProfit() { return autosellProfit; }
    @Override
    public String getInvalidShopSection() { return invalidShopSection; }
    @Override
    public String getBackgroundPaneText() { return backgroundPaneText; }
    @Override
    public String getGuiTitleShop() { return guiTitleShop; }
    @Override
    public String getPermissionDenied() { return permissionDenied; }
    @Override
    public String getAdminReloadingShops() { return adminReloadingShops; }
    @Override
    public String getAdminShopsReloaded() { return adminShopsReloaded; }
    @Override
    public String getAdminPricesExported() { return adminPricesExported; }
    @Override
    public String getAdminPricesImported() { return adminPricesImported; }
    @Override
    public String getAdminShopRemoved() { return adminShopRemoved; }
    @Override
    public String getAdminShopNotFound() { return adminShopNotFound; }
    @Override
    public String getAdminInvalidPrice() { return adminInvalidPrice; }
    @Override
    public String getAdminPriceSet() { return adminPriceSet; }
    @Override
    public String getAdminPricesUpdating() { return adminPricesUpdating; }
    @Override
    public String getAdminPricesUpdated() { return adminPricesUpdated; }
    @Override
    public String getGuiBackToMenu() { return guiBackToMenu; }
    @Override
    public String getGuiGoToPage() { return guiGoToPage; }
    @Override
    public String getPlayersOnly() { return playersOnly; }
    @Override
    public String getAutosellNoEnchanted() { return autosellNoEnchanted; }
    @Override
    public String getLoanUsage() { return loanUsage; }
    @Override
    public String getLoanPaidBack() { return loanPaidBack; }
    @Override
    public String getLoanNotEnoughMoneyPayback() { return loanNotEnoughMoneyPayback; }
    @Override
    public String getLoanInvalidAmount() { return loanInvalidAmount; }
    @Override
    public String getLoanLimitReached() { return loanLimitReached; }
    @Override
    public String getLoanNotEnoughMoneyLoan() { return loanNotEnoughMoneyLoan; }
    @Override
    public String getLoanInfo() { return loanInfo; }
    @Override
    public String getSellSuccess() { return sellSuccess; }
    @Override
    public String getGuiTitleSellPanel() { return guiTitleSellPanel; }
    @Override
    public String getMarketOpeningGui() { return marketOpeningGui; }
    @Override
    public String getAutotradeInvalidShopName() { return autotradeInvalidShopName; }
    @Override
    public String getAutotradeDisabled() { return autotradeDisabled; }
    @Override
    public String getAutotradeEnabled() { return autotradeEnabled; }
    @Override
    public String getAutotradeToggled() { return autotradeToggled; }
    @Override
    public String getLoanTakenSuccess() { return loanTakenSuccess; }
    @Override
    public String getPlayerNotFound() { return playerNotFound; }
    @Override
    public String getAdminMigrationUsage() { return adminMigrationUsage; }
    @Override
    public String getAdminMigrationMysqlRequired() { return adminMigrationMysqlRequired; }
    @Override
    public String getAdminMigrationStarted() { return adminMigrationStarted; }
    @Override
    public String getAdminMigrationNoFile() { return adminMigrationNoFile; }
    @Override
    public String getAdminMigrationNoData() { return adminMigrationNoData; }
    @Override
    public String getAdminMigrationComplete() { return adminMigrationComplete; }
    @Override
    public String getAdminMigrationError() { return adminMigrationError; }

    @Override
    public List<String> getShopLore() { return shopLore; }
    @Override
    public List<String> getShopGdpLore() { return shopGdpLore; }
    @Override
    public List<String> getPurchaseBuyLore() { return purchaseBuyLore; }
    @Override
    public List<String> getPurchaseEnchantLore() { return purchaseEnchantLore; }
    @Override
    public List<String> getPurchaseSellLore() { return purchaseSellLore; }
    @Override
    public List<String> getAutosellLore() { return autosellLore; }
    @Override
    public List<String> getHelp() { return help; }
    @Override
    public List<String> getAdminHelp() { return adminHelp; }
    @Override
    public List<String> getTutorial() { return tutorial; }

    @Override
    public String getMessage(String key) {
        return messagesYml.getString(key);
    }
}
