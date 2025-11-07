package unprotesting.com.github.config;

import unprotesting.com.github.data.CollectFirst;
import unprotesting.com.github.data.CollectFirst.CollectFirstSetting;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import unprotesting.com.github.AutoTune;
import unprotesting.com.github.util.AutoTuneLogger;
import unprotesting.com.github.util.Format;

/**
 * The class for loading and storing the configuration options.
 */
public class Config {

    // The static instance of the config.
    private static Config config;
    // The list of config filenames.
    private static final String[] filenames = { "config.yml", "shops.yml",
        "playerdata.yml", "messages.yml" };
    // The list of files.
    private static File[] files;
    // The list of configs.
    private static YamlConfiguration[] configs;

    @Getter private final boolean databaseEnabled;
    @Getter private final double timePeriod;
    @Getter private final double volatility;
    @Getter private final double sellPriceDifference;
    @Getter private final boolean durabilityFunction;
    @Getter private final Integer minimumPlayers;
    @Getter private final double interest;
    @Getter private final double tutorialUpdate;
    @Getter private final boolean webServer;
    @Getter private final Integer port;
    @Getter private final String background;
    @Getter private final String logLevel;
    @Getter private final String locale;
    @Getter private final boolean enableSellLimits;
    @Getter private final boolean enableCollection;
    @Getter private final boolean enableLoans;
    @Getter private final int maxActiveLoans;

    // New field for collect-first default setting
    @Getter private final CollectFirstSetting collectFirstDefault;

    // New fields for pricing configuration
    @Getter private final boolean allowUncraftEdges;
    @Getter private final boolean allowCompressionEdges;
    @Getter private final boolean treatReversibleAsDerived;
    @Getter private final double antiArbitrageFee;

    @Getter private final double startPrice;

    @Getter private final String notInShop;
    @Getter private final String notEnoughMoney;
    @Getter private final String notEnoughSpace;
    @Getter private final String notEnoughItems;

    @Getter private final String runOutOfBuys;
    @Getter private final String runOutOfSells;
    @Getter private final String shopPurchase;
    @Getter private final String shopSell;
    @Getter private final String holdItemInHand;
    @Getter private final String enchantmentError;
    @Getter private final String autosellProfit;
    @Getter private final String invalidShopSection;
    @Getter private final String backgroundPaneText;
    @Getter private final String guiTitleShop;
    @Getter private final String permissionDenied;
    @Getter private final String adminReloadingShops;
    @Getter private final String adminShopsReloaded;
    @Getter private final String adminPricesExported;
    @Getter private final String adminPricesImported;
    @Getter private final String adminShopRemoved;
    @Getter private final String adminShopNotFound;
    @Getter private final String adminInvalidPrice;
    @Getter private final String adminPriceSet;
    @Getter private final String adminPricesUpdating;
    @Getter private final String adminPricesUpdated;
    @Getter private final String guiBackToMenu;
    @Getter private final String guiGoToPage;

    @Getter private final String playersOnly;
    @Getter private final String autosellNoEnchanted;
    @Getter private final String loanUsage;
    @Getter private final String loanPaidBack;
    @Getter private final String loanNotEnoughMoneyPayback;
    @Getter private final String loanInvalidAmount;
    @Getter private final String loanLimitReached;
    @Getter private final String loanNotEnoughMoneyLoan;
    @Getter private final String loanInfo;
    @Getter private final String sellSuccess;
    @Getter private final String guiTitleSellPanel;

    @Getter private final String marketOpeningGui;
    @Getter private final String autotradeInvalidShopName;
    @Getter private final String autotradeDisabled;
    @Getter private final String autotradeEnabled;
    @Getter private final String autotradeToggled;
    @Getter private final String loanTakenSuccess;
    @Getter private final String playerNotFound;

    @Getter private final String adminMigrationUsage;
    @Getter private final String adminMigrationMysqlRequired;
    @Getter private final String adminMigrationStarted;
    @Getter private final String adminMigrationNoFile;
    @Getter private final String adminMigrationNoData;
    @Getter private final String adminMigrationComplete;
    @Getter private final String adminMigrationError;

    @Getter private final List<String> shopLore;
    @Getter private final List<String> shopGdpLore;
    @Getter private final List<String> purchaseBuyLore;
    @Getter private final List<String> purchaseSellLore;
    @Getter private final List<String> autosellLore;
    @Getter private final List<String> help;
    @Getter private final List<String> adminHelp;
    @Getter private final List<String> tutorial;

    @Getter private final ConfigurationSection shops;
    @Getter private final ConfigurationSection sections;
    @Getter private final ConfigurationSection economicEvents;
    @Getter private ConfigurationSection autosell;

    @Getter private final String guiLockedStyle;
    @Getter private final boolean guiLockedTipsMetalsHint;



    // MANUALLY ADDED GETTERS END

    public String getMessage(String key) {
        return configs[3].getString(key);
    }


    /**
     * Initializes the config files.
     */
    public static void init() {
        try {
            initializeConfig();
        } catch (Exception e) {
            Format.getLog().info("Failed to initialize config.");
            Format.getLog().config(e.toString());
        }
    }

    private static void initializeConfig() {
        files = new File[filenames.length];
        configs = new YamlConfiguration[filenames.length];
        config = new Config();
    }

    public static YamlConfiguration getShopsConfig() {
        return configs[1];
    }

    public static YamlConfiguration getConfigConfig() {
        return configs[0];
    }

    /**
     * Gets the config.
     *
     * @return the config object
     */
    public static Config get() {
        return config;
    }

    private Config() {
        AutoTune instance = AutoTune.getInstance();
        for (int i = 0; i < filenames.length; i++) {
            if (!new File(instance.getDataFolder(), filenames[i]).exists()) {
                instance.saveResource(filenames[i], false);
            }
        }

        saveWebFolder();

        for (int i = 0; i < filenames.length; i++) {
            File file = new File(instance.getDataFolder(), filenames[i]);
            if (!file.exists()) {
                instance.getLogger().info("Failed to load config file: " + filenames[i]);
                continue;
            }
            files[i] = file;
            configs[i] = YamlConfiguration.loadConfiguration(files[i]);
        }

        this.databaseEnabled = configs[0].getBoolean("database-enabled", false);
        this.logLevel = configs[0].getString("log-level", "INFO");
        Format.loadLogger(Level.parse(logLevel));
        AutoTuneLogger logger = Format.getLog();

        this.timePeriod = configs[0].getDouble("time-period", 30);
        logger.finer("Time period: " + timePeriod);
        this.volatility = configs[0].getDouble("volatility", 0.5);
        logger.finer("Volatility: " + volatility);
        this.sellPriceDifference = configs[0].getDouble("sell-price-difference", 20);
        logger.finer("Sell price difference: " + sellPriceDifference);
        this.durabilityFunction = configs[0].getBoolean("durability-function", true);
        logger.finer("Durability function: " + durabilityFunction);
        this.minimumPlayers = configs[0].getInt("minimum-players", 2);
        logger.finer("Minimum players: " + minimumPlayers);
        this.interest = configs[0].getDouble("interest", 0.05);
        logger.finer("Interest: " + interest);
        this.tutorialUpdate = configs[0].getDouble("tutorial-update", 300);
        logger.finer("Tutorial update: " + tutorialUpdate);
        this.webServer = configs[0].getBoolean("web-server", true);
        logger.finer("Web-Server: " + webServer);
        this.port = configs[0].getInt("port", 8989);
        logger.finer("Port: " + port);
        this.background = configs[0].getString("background", "BLACK_STAINED_GLASS_PANE");
        logger.finer("Background: " + background);
        this.locale = configs[0].getString("locale", "en_US");
        Format.loadLocale(this.locale);
        logger.finer("Locale: " + locale);
        this.enableSellLimits = configs[0].getBoolean("enable-sell-limits", false);
        logger.finer("Skip Max Limits: " + enableSellLimits);
        this.enableCollection = configs[0].getBoolean("enable-collection", true);
        logger.finer("Collection Enabled: " + enableCollection);
        this.enableLoans = configs[0].getBoolean("enable-loans", false);
        logger.finer("Loans Enabled: " + enableLoans);
        this.maxActiveLoans = configs[0].getInt("max-active-loans", 5);
        logger.finer("Max Active Loans: " + maxActiveLoans);

        // Load collect-first default setting
        String collectFirstDefaultString = configs[0].getString("access.collect-first.default", "NONE");
        this.collectFirstDefault = CollectFirstSetting.valueOf(collectFirstDefaultString.toUpperCase());
        logger.finer("Collect First Default: " + collectFirstDefault);

        // Load new pricing configuration fields
        this.allowUncraftEdges = configs[0].getBoolean("pricing.allow-uncraft-edges", false);
        logger.finer("Allow Uncraft Edges: " + allowUncraftEdges);
        this.allowCompressionEdges = configs[0].getBoolean("pricing.allow-compression-edges", true);
        logger.finer("Allow Compression Edges: " + allowCompressionEdges);
        this.treatReversibleAsDerived = configs[0].getBoolean("pricing.treat-reversible-as-derived", true);
        logger.finer("Treat Reversible As Derived: " + treatReversibleAsDerived);
        this.antiArbitrageFee = configs[0].getDouble("pricing.anti-arbitrage-fee", 0.01);
        logger.finer("Anti Arbitrage Fee: " + antiArbitrageFee);

        this.startPrice = configs[0].getDouble("start-price", 10.0);
        logger.finer("Default Start Price: " + startPrice);

        this.notInShop = configs[3].getString("not-in-shop");
        logger.finest("Not in shop: " + notInShop);
        this.notEnoughMoney = configs[3].getString("not-enough-money");
        logger.finest("Not enough money: " + notEnoughMoney);
        this.notEnoughSpace = configs[3].getString("not-enough-space");
        logger.finest("Not enough space: " + notEnoughSpace);
        this.notEnoughItems = configs[3].getString("not-enough-items");
        logger.finest("Not enough items: " + notEnoughItems);

        this.runOutOfBuys = configs[3].getString("run-out-of-buys");
        logger.finest("Run out of buys: " + runOutOfBuys);
        this.runOutOfSells = configs[3].getString("run-out-of-sells");
        logger.finest("Run out of sells: " + runOutOfSells);
        this.shopPurchase = configs[3].getString("shop-purchase");
        logger.finest("Shop purchase: " + shopPurchase);
        this.shopSell = configs[3].getString("shop-sell");
        logger.finest("Shop sell: " + shopSell);
        this.holdItemInHand = configs[3].getString("hold-item-in-hand");
        logger.finest("Hold item in hand: " + holdItemInHand);
        this.enchantmentError = configs[3].getString("enchantment-error");
        logger.finest("Enchantment error: " + enchantmentError);
        this.autosellProfit = configs[3].getString("autosell-profit");
        logger.finest("Autosell profit: " + autosellProfit);
        this.invalidShopSection = configs[3].getString("invalid-shop-section");
        logger.finest("Invalid shop section: " + invalidShopSection);
        this.backgroundPaneText = configs[3].getString("background-pane-text", "<obf>|</obf>");
        logger.finest("Background pane text: " + backgroundPaneText);

        this.guiLockedStyle = configs[0].getString("gui.locked_style", "ghost");
        logger.finer("GUI Locked Style: " + guiLockedStyle);
        this.guiLockedTipsMetalsHint = configs[0].getBoolean("gui.locked_tips.metals_hint", true);
        logger.finer("GUI Locked Tips Metals Hint: " + guiLockedTipsMetalsHint);

        this.guiTitleShop = configs[3].getString("gui-title-shop");
        logger.finest("GUI Title Shop: " + guiTitleShop);
        this.permissionDenied = configs[3].getString("permission-denied");
        logger.finest("Permission Denied: " + permissionDenied);
        this.adminReloadingShops = configs[3].getString("admin-reloading-shops");
        logger.finest("Admin Reloading Shops: " + adminReloadingShops);
        this.adminShopsReloaded = configs[3].getString("admin-shops-reloaded");
        logger.finest("Admin Shops Reloaded: " + adminShopsReloaded);
        this.adminPricesExported = configs[3].getString("admin-prices-exported");
        logger.finest("Admin Prices Exported: " + adminPricesExported);
        this.adminPricesImported = configs[3].getString("admin-prices-imported");
        logger.finest("Admin Prices Imported: " + adminPricesImported);
        this.adminShopRemoved = configs[3].getString("admin-shop-removed");
        logger.finest("Admin Shop Removed: " + adminShopRemoved);
        this.adminShopNotFound = configs[3].getString("admin-shop-not-found");
        logger.finest("Admin Shop Not Found: " + adminShopNotFound);
        this.adminInvalidPrice = configs[3].getString("admin-invalid-price");
        logger.finest("Admin Invalid Price: " + adminInvalidPrice);
        this.adminPriceSet = configs[3].getString("admin-price-set");
        logger.finest("Admin Price Set: " + adminPriceSet);
        // Load new fields
        this.adminPricesUpdating = configs[3].getString("admin-prices-updating");
        logger.finest("Admin Prices Updating: " + adminPricesUpdating);
        this.adminPricesUpdated = configs[3].getString("admin-prices-updated");
        logger.finest("Admin Prices Updated: " + adminPricesUpdated);

        this.guiBackToMenu = configs[3].getString("gui-back-to-menu");
        logger.finest("GUI Back to Menu: " + guiBackToMenu);
        this.guiGoToPage = configs[3].getString("gui-go-to-page");
        logger.finest("GUI Go to Page: " + guiGoToPage);

        this.playersOnly = configs[3].getString("players-only");
        logger.finest("Players Only: " + playersOnly);
        this.autosellNoEnchanted = configs[3].getString("autosell-no-enchanted");
        logger.finest("Autosell No Enchanted: " + autosellNoEnchanted);
        this.loanUsage = configs[3].getString("loan-usage");
        logger.finest("Loan Usage: " + loanUsage);
        this.loanPaidBack = configs[3].getString("loan-paid-back");
        logger.finest("Loan Paid Back: " + loanPaidBack);
        this.loanNotEnoughMoneyPayback = configs[3].getString("loan-not-enough-money-payback");
        logger.finest("Loan Not Enough Money Payback: " + loanNotEnoughMoneyPayback);
        this.loanInvalidAmount = configs[3].getString("loan-invalid-amount");
        logger.finest("Loan Invalid Amount: " + loanInvalidAmount);
        this.loanLimitReached = configs[3].getString("loan-limit-reached");
        logger.finest("Loan Limit Reached: " + loanLimitReached);
        this.loanNotEnoughMoneyLoan = configs[3].getString("loan-not-enough-money-loan");
        logger.finest("Loan Not Enough Money Loan: " + loanNotEnoughMoneyLoan);
        this.loanInfo = configs[3].getString("loan-info");
        logger.finest("Loan Info: " + loanInfo);
        this.sellSuccess = configs[3].getString("sell-success");
        logger.finest("Sell Success: " + sellSuccess);
        this.guiTitleSellPanel = configs[3].getString("gui-title-sell-panel");
        logger.finest("GUI Title Sell Panel: " + guiTitleSellPanel);

        this.marketOpeningGui = configs[3].getString("market-opening-gui");
        logger.finest("Market Opening GUI: " + marketOpeningGui);
        this.autotradeInvalidShopName = configs[3].getString("autotrade-invalid-shop-name");
        logger.finest("Autotrade Invalid Shop Name: " + autotradeInvalidShopName);
        this.autotradeDisabled = configs[3].getString("autotrade-disabled");
        logger.finest("Autotrade Disabled: " + autotradeDisabled);
        this.autotradeEnabled = configs[3].getString("autotrade-enabled");
        logger.finest("Autotrade Enabled: " + autotradeEnabled);
        this.autotradeToggled = configs[3].getString("autotrade-toggled");
        logger.finest("Autotrade Toggled: " + autotradeToggled);
        this.loanTakenSuccess = configs[3].getString("loan-taken-success");
        logger.finest("Loan Taken Success: " + loanTakenSuccess);
        this.playerNotFound = configs[3].getString("player-not-found");
        logger.finest("Player Not Found: " + playerNotFound);

        // Admin Migration Messages
        this.adminMigrationUsage = configs[3].getString("admin-migration-usage");
        logger.finest("Admin Migration Usage: " + adminMigrationUsage);
        this.adminMigrationMysqlRequired = configs[3].getString("admin-migration-mysql-required");
        logger.finest("Admin Migration MySQL Required: " + adminMigrationMysqlRequired);
        this.adminMigrationStarted = configs[3].getString("admin-migration-started");
        logger.finest("Admin Migration Started: " + adminMigrationStarted);
        this.adminMigrationNoFile = configs[3].getString("admin-migration-no-file");
        logger.finest("Admin Migration No File: " + adminMigrationNoFile);
        this.adminMigrationNoData = configs[3].getString("admin-migration-no-data");
        logger.finest("Admin Migration No Data: " + adminMigrationNoData);
        this.adminMigrationComplete = configs[3].getString("admin-migration-complete");
        logger.finest("Admin Migration Complete: " + adminMigrationComplete);
        this.adminMigrationError = configs[3].getString("admin-migration-error");
        logger.finest("Admin Migration Error: " + adminMigrationError);

        this.shopLore = configs[3].getStringList("shop-lore");
        logger.finest("Shop lore: " + Arrays.toString(shopLore.toArray()));
        this.shopGdpLore = configs[3].getStringList("shop-gdp-lore");
        logger.finest("GDP shop lore: " + Arrays.toString(shopGdpLore.toArray()));
        this.purchaseBuyLore = configs[3].getStringList("purchase-buy-lore");
        logger.finest("Purchase buy lore: " + Arrays.toString(purchaseBuyLore.toArray()));
        this.purchaseSellLore = configs[3].getStringList("purchase-sell-lore");
        logger.finest("Purchase sell lore: " + Arrays.toString(purchaseSellLore.toArray()));
        this.autosellLore = configs[3].getStringList("autosell-lore");
        logger.finest("Autosell lore: " + Arrays.toString(autosellLore.toArray()));
        this.help = configs[3].getStringList("help");
        logger.finest("Help: " + Arrays.toString(help.toArray()));
        this.adminHelp = configs[3].getStringList("admin-help");
        logger.finest("AdminHelp: " + Arrays.toString(adminHelp.toArray()));
        this.tutorial = configs[3].getStringList("tutorial");
        logger.finest("Tutorial: " + Arrays.toString(tutorial.toArray()));

        this.shops = configs[1].getConfigurationSection("shops");
        logger.finer("Loaded shops configuration.");
        this.sections = configs[1].getConfigurationSection("sections");
        logger.finer("Loaded sections configuration.");
        this.economicEvents = configs[0].getConfigurationSection("economic-events");
        logger.finer("Loaded economic events configuration.");

        this.autosell = configs[2].getConfigurationSection("autosell");
        logger.finer("Loaded autosell configuration.");
    }

    public void setAutosell(@NotNull ConfigurationSection section) {
        configs[2].set("autosell", section);
        try {
            configs[2].save(files[2]);
        } catch (IOException e) {
            Format.getLog().severe("Could not save autosell data to playerdata.yml!");
            e.printStackTrace();
        }
    }

    private void saveWebFolder() {
        AutoTune instance = AutoTune.getInstance();
        File webFolder = new File(instance.getDataFolder(), "web");
        if (!webFolder.exists()) {
            webFolder.mkdir();
        }
        File buildFolder = new File(webFolder, "build");
        if (!buildFolder.exists()) {
            buildFolder.mkdir();
        }
        String[] files = { "index.html", "favicon.png",
            "global.css", "build/bundle.js", "build/bundle.js.map" };
        for (String file : files) {
            File webFile = new File(webFolder, file);
            if (!webFile.exists()) {
                instance.saveResource("web/" + file, true);
            }
        }
    }
}
