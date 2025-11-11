package com.github.lye;

import lombok.Getter;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import com.github.lye.commands.AutotradeCommand;
import com.github.lye.commands.LoanCommand;
import com.github.lye.commands.SellCommand;
import com.github.lye.commands.MarketCommand;
// Removed import for com.yourplugin.commands.TradeFlowCommand;
import com.github.lye.commands.ImportShopsCommand;
import com.github.lye.commands.MigrateDatabaseCommand;
import com.github.lye.config.Config;
import com.github.lye.data.Database;
import com.github.lye.database.MySQLConnector;
import com.github.lye.database.ShopData;
// Removed explicit imports for non-existent Event classes
import com.github.lye.server.LocalServer;
import com.github.lye.util.EconomyUtil;
import com.github.lye.commands.core.CommandManager;
import com.github.lye.gui.ShopGuiManager;
import com.github.lye.util.FoliaSchedulers;
import com.github.lye.util.Format;
import com.github.lye.events.EconomicEventManager;
import com.github.lye.access.AccessResolver;
import com.github.lye.access.DefaultAccessResolver;
import com.github.lye.access.rules.CollectFirstRule;
import com.github.lye.config.ConfigResolver;
import com.github.lye.gateway.AccessGateway;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;

import com.github.lye.access.AccessRule;

// Imports pour le nouveau système de pricing
import com.yourplugin.pricing.engine.PriceEngine;
import com.yourplugin.pricing.engine.DefaultPriceEngine;
import com.yourplugin.pricing.service.PriceService;
import com.yourplugin.pricing.service.DefaultPriceService;
import com.yourplugin.pricing.model.PriceSnapshot;
import com.yourplugin.pricing.service.AuditService;
import com.yourplugin.pricing.service.DefaultAuditService;
import com.yourplugin.pricing.model.PricingParams;
import com.yourplugin.pricing.util.ConfigLoader;
import com.yourplugin.pricing.model.ItemConfig;
import com.yourplugin.pricing.model.GlobalPricingConfig;
import com.github.lye.data.Shop;

import java.util.HashMap;
import java.util.Map;
import com.github.lye.data.Loan;
import com.github.lye.database.LoanData;

import com.github.lye.data.Transaction;
import com.github.lye.database.TransactionData;
import com.github.lye.data.CraftingDependencyResolver;

import com.github.lye.database.EconomyDataData;
import com.github.lye.database.PlayerData;
import com.github.lye.database.PlayerCollectionData;
import com.github.lye.database.ServerCollectionData;
import com.github.lye.database.FilePlayerCollectionData;
import com.github.lye.database.FileServerCollectionData;
import com.github.lye.database.IPlayerCollectionData;
import com.github.lye.database.IServerCollectionData;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap; // Add this import

import com.yourplugin.pricing.database.PriceDatabaseAPI;
import com.yourplugin.pricing.database.MySQLPriceDatabaseAPIImpl;
import com.yourplugin.pricing.model.ItemId;
import com.yourplugin.pricing.model.PricingData;
import com.yourplugin.pricing.PricingManager;

import java.util.logging.Level;




/**
 * The main class of TradeFlow.
 */
public class TradeFlow extends JavaPlugin {

    @Getter
    private static TradeFlow instance;

    private MySQLConnector mysqlConnector;
    private boolean mySqlEnabled = false;

    private ShopData shopData;
    private LoanData loanData;
    private TransactionData transactionData;
    private EconomyDataData economyDataData;
    private PlayerData playerData;
    private com.github.lye.database.GlobalStockData globalStockData;
    private com.github.lye.database.ServerStateData serverStateData;

    IPlayerCollectionData playerCollectionData;
    IServerCollectionData serverCollectionData;

    private final Map<String, Shop> loadedShops = new ConcurrentHashMap<>();
    private final Map<String, Loan> loadedLoans = new ConcurrentHashMap<>();
    private final Map<String, Transaction> loadedTransactions = new ConcurrentHashMap<>();
    private final Map<String, double[]> loadedEconomyData = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> loadedAutosellSettings = new ConcurrentHashMap<>();

    private EconomicEventManager economicEventManager;
    private com.github.lye.data.GlobalStockManager globalStockManager;
    private java.util.List<String> sortedShopItems;

    @Getter
    private AccessGateway accessGateway;
    @Getter
    private ConfigResolver configResolver;
    @Getter
    private AccessResolver accessResolver;

    @Getter
    private PriceService priceService;
    @Getter
    private com.yourplugin.pricing.gui.FamilyRegistry familyRegistry;

    @Getter
    private ShopGuiManager shopGuiManager;

    @Getter
    private Database database;

    private PriceDatabaseAPI priceDatabaseAPI; // Declare the field
    private PricingManager pricingManager; // Declare pricingManager field

    public MySQLConnector getMysqlConnector() {
        return mysqlConnector;
    }

    public boolean isMySqlEnabled() {
        return mySqlEnabled;
    }

    public ShopData getShopData() {
        return shopData;
    }

    public LoanData getLoanData() {
        return loanData;
    }

    public TransactionData getTransactionData() {
        return transactionData;
    }

    public EconomyDataData getEconomyDataData() {
        return economyDataData;
    }

    public PlayerData getPlayerData() {
        return playerData;
    }

    public com.github.lye.database.GlobalStockData getGlobalStockData() {
        return globalStockData;
    }

    public com.github.lye.database.ServerStateData getServerStateData() {
        return serverStateData;
    }

    public Map<String, Shop> getLoadedShops() {
        return loadedShops;
    }

    public Map<String, Loan> getLoadedLoans() {
        return loadedLoans;
    }

    public Map<String, Transaction> getLoadedTransactions() {
        return loadedTransactions;
    }

    public Map<String, double[]> getLoadedEconomyData() {
        return loadedEconomyData;
    }

    public Map<UUID, Set<String>> getLoadedAutosellSettings() {
        return loadedAutosellSettings;
    }

    public EconomicEventManager getEconomicEventManager() {
        return economicEventManager;
    }

    public com.github.lye.data.GlobalStockManager getGlobalStockManager() {
        return globalStockManager;
    }

    public java.util.List<String> getSortedShopItems() {
        return sortedShopItems;
    }

    @Override
    public void onEnable() {
        instance = this;

        this.playerCollectionData = null; // Initialize to null
        this.serverCollectionData = null; // Initialize to null

        // Initialiser le logger avant toute autre initialisation qui pourrait l'utiliser
        Format.loadLogger(java.util.logging.Level.FINEST);

        EconomyUtil.setupLocalEconomy(Bukkit.getServer());
        Config.init();
        com.github.lye.config.EventsConfig.init();

        // Initialize data handlers based on database configuration
        IPlayerCollectionData localPlayerCollectionData;
        IServerCollectionData localServerCollectionData;

        if (Config.get().isDatabaseEnabled()) {
            try {
                this.mysqlConnector = new MySQLConnector(this.getConfig());
                this.mySqlEnabled = true;
                getLogger().info("[DEBUG] TradeFlow.onEnable(): Database instantiated (MySQL path).");

                // Initialize priceDatabaseAPI here
                this.priceDatabaseAPI = new MySQLPriceDatabaseAPIImpl(this.getConfig(), getLogger());

                localPlayerCollectionData = new PlayerCollectionData(this, this.mysqlConnector);
                localPlayerCollectionData.createTable();

                localServerCollectionData = new ServerCollectionData(this, this.mysqlConnector);
                localServerCollectionData.createTable();

                this.shopData = new ShopData(this, this.mysqlConnector);
                this.shopData.loadAllShops();

                // Initialize DAO handlers (MySQL path)
                this.loanData = new LoanData(this, this.mysqlConnector);
                this.loanData.createTables();

                this.transactionData = new TransactionData(this, this.mysqlConnector);
                this.transactionData.createTables();

                this.economyDataData = new EconomyDataData(this, this.mysqlConnector);
                this.economyDataData.createTables();

                getLogger().info("[DEBUG] Database TransactionData initialized (MySQL)");

            } catch (Exception e) {
                getLogger().severe("Could not establish MySQL connection! Falling back to file-based storage. Error: " + e.getMessage());
                this.mySqlEnabled = false;
                localPlayerCollectionData = new FilePlayerCollectionData(this);
                localServerCollectionData = new FileServerCollectionData(this);

                // Initialize priceDatabaseAPI for file-based storage
                this.priceDatabaseAPI = new PlaceholderPriceDatabaseAPI();
            }
        } else {
            getLogger().info("Using file-based storage for player and server collections.");
            localPlayerCollectionData = new FilePlayerCollectionData(this);
            localServerCollectionData = new FileServerCollectionData(this);

            // Initialize priceDatabaseAPI for file-based storage
            this.priceDatabaseAPI = new PlaceholderPriceDatabaseAPI();
        }

        // Assign to class members
        this.playerCollectionData = localPlayerCollectionData;
        this.serverCollectionData = localServerCollectionData;

        // Initialize Access Control
        this.accessGateway = new AccessGateway(this, this.playerCollectionData, this.serverCollectionData);
        this.configResolver = new ConfigResolver(Config.getConfigConfig(), Config.getShopsConfig());
        List<AccessRule> rules = new ArrayList<>(); // Declare rules here
        rules.add(new CollectFirstRule());
        this.accessResolver = new DefaultAccessResolver(rules, accessGateway::isAccessReady, accessGateway, configResolver);

        // Load and Merge Configurations for FamilyRegistry
        File defaultConfig = new File(getDataFolder(), "config.yml");
        File shopConfig = new File(getDataFolder(), "shops.yml");
        YamlConfiguration mergedConfig = com.yourplugin.pricing.config.ConfigMerger.mergeConfigs(defaultConfig, shopConfig);
        List<com.yourplugin.pricing.model.Family> families = com.yourplugin.pricing.config.FamiliesConfigLoader.loadFamilies(mergedConfig);
        this.familyRegistry = new com.yourplugin.pricing.gui.FamilyRegistry(families);

        // Initialize AuditService and PricingParams for PricingManager
        AuditService auditService = new DefaultAuditService(getLogger());
        PricingParams pricingParams = new PricingParams(
            0.10, // defaultMargin
            0.05, // defaultTax
            0.10, // defaultVolatility
            0.02, // machineTimeCostPerSecond
            0.7,  // byproductRatio
            new HashMap<>(), // fuelCosts
            (outputItemId) -> 0.0 // Dummy toolWearCostFn
        );

        // Initialize PricingManager
        this.pricingManager = new PricingManager(auditService, pricingParams);
        this.pricingManager.start(); // Start the pricing manager

        // Initialize PriceService
        this.priceService = this.pricingManager.priceService();

        // Warm up the gateway
        accessGateway.markStorageReady();
        accessGateway.warmFromDatabase();

        // NOW instantiate the Database object, which uses the loaded data
        com.github.lye.data.Database databaseInstance = new com.github.lye.data.Database();
        com.github.lye.data.Database.setInstance(databaseInstance);
        this.database = databaseInstance; // Assign to the field
        databaseInstance.initialize(this);
        getLogger().info(String.format("[DEBUG] TradeFlow.onEnable(): Database instantiated (%s path).", mySqlEnabled ? "MySQL" : "File-based"));

        getLogger().info("[DEBUG] Database All DAO handlers loaded");

        getLogger().info("[DEBUG] TradeFlow.onEnable(): priceService before ShopGuiManager instantiation: " + (this.priceService == null ? "null" : "initialized"));
        this.shopGuiManager = new ShopGuiManager(this, this.accessResolver, this.configResolver, this.database, Config.get(), this.priceService, this.familyRegistry); // Instantiate here

        // Re-initialize managers with DB handlers
        this.economicEventManager = new EconomicEventManager(this, mySqlEnabled ? this.serverStateData : null);
        this.globalStockManager = new com.github.lye.data.GlobalStockManager(this, mySqlEnabled ? this.globalStockData : null);

        // Initial calculations
        databaseInstance.updateRelations();        this.setupEvents();
        getServer().getGlobalRegionScheduler().runDelayed(this, task -> this.setupCommands(), 1L);

        new Metrics(this, 9687);

        LocalServer.initialize();
    }

    @Override
    public void onDisable() {
        if (mySqlEnabled && mysqlConnector != null) {
            mysqlConnector.close();
            getLogger().info("MySQL connection closed.");
        } else if (Database.get() != null) {
            Database.get().close();
        }
        getLogger().info("TradeFlow is now disabled!");
    }

    // Methods to be added/made public
    public void setupEvents() {
        PluginManager pm = getServer().getPluginManager();
        // Collection tracking (pickup/craft/invclick) to drive collect-first unlocks
        new com.github.lye.events.PlayerCollectionListener(this, this.accessGateway, this.shopGuiManager, this.configResolver);
        // pm.registerEvents(new PlayerJoinLeave(this), this); // Commented out due to missing class
        // pm.registerEvents(new InventoryClose(this), this); // Commented out due to missing class
        // pm.registerEvents(new InventoryClick(this), this); // Commented out due to missing class
        // pm.registerEvents(new ShopSign(this), this); // Commented out due to missing class
        // pm.registerEvents(new PlayerChat(this), this); // Commented out due to missing class
        // pm.registerEvents(new BlockBreak(this), this); // Commented out due to missing class
        // pm.registerEvents(new Crafting(this), this); // Commented out due to missing class
        // pm.registerEvents(new Trades(this), this); // Commented out due to missing class
        // pm.registerEvents(new ShopCreate(this), this); // Commented out due to missing class
        // pm.registerEvents(new ShopItemInteract(this), this); // Commented out due to missing class
        // pm.registerEvents(new PlayerInteract(this), this); // Commented out due to missing class
        // pm.registerEvents(new SignChange(this), this); // Commented out due to missing class
        // pm.registerEvents(new PistonEvent(this), this); // Commented out due to missing class
    }

    public void setupCommands() {
        CommandManager commandManager = new CommandManager(this);
        commandManager.registerCommand(new AutotradeCommand(this, this.shopGuiManager));
        commandManager.registerCommand(new SellCommand(this));
        commandManager.registerCommand(new LoanCommand(this));
        commandManager.registerCommand(new MarketCommand(this, this.shopGuiManager));
        // commandManager.registerCommand(new TradeFlowCommand(this.pricingManager)); // Commented out as the class was removed
        commandManager.registerCommand(new ImportShopsCommand(this));
        commandManager.registerCommand(new MigrateDatabaseCommand(this));

        // Register the new commands using Bukkit's command map
        this.getCommand("tfmarket").setExecutor(new com.github.lye.commands.MarketCommand(this, this.shopGuiManager));
        this.getCommand("tfsetprice").setExecutor(new com.github.lye.commands.TradeFlowSetPriceCommand(this));
        this.getCommand("tfupdate").setExecutor(new com.github.lye.commands.TradeFlowUpdateCommand(this));
        this.getCommand("tfimport").setExecutor(new com.github.lye.commands.ImportShopsCommand(this));
    }

    public void recalculatePrices() {
        getLogger().info("[AutoPricing] Recalculating prices...");
        this.pricingManager.start(); // This will trigger the pricing manager to compute a new snapshot
        PriceSnapshot snapshot = this.pricingManager.priceService().getCurrentSnapshot();
        getLogger().info("[AutoPricing] Snapshot: finite=" + snapshot.getPrices().size() + ", breakdowns=" + snapshot.getBreakdowns().size());
    }
    // Placeholder for your actual PriceDatabaseAPI implementation
    private static class PlaceholderPriceDatabaseAPI implements PriceDatabaseAPI {
        @Override
        public CompletableFuture<java.util.Optional<com.yourplugin.pricing.model.PricingData>> getPricingData(com.yourplugin.pricing.model.ItemId itemId) {
            return CompletableFuture.completedFuture(java.util.Optional.empty());
        }

        @Override
        public CompletableFuture<Void> savePricingData(com.yourplugin.pricing.model.PricingData pricingData) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Boolean> itemExists(com.yourplugin.pricing.model.ItemId itemId) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletableFuture<Void> initialize() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> shutdown() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Double getOrNull(String anyKey) {
            return null;
        }

        @Override
        public void upsert(String anyKey, double price) {
            // Do nothing
        }
    }
}
