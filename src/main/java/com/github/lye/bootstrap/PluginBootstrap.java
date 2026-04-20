package com.github.lye.bootstrap;

import com.github.lye.TradeFlow;
import com.github.lye.concurrent.AsyncExecutor;
import com.github.lye.gateway.AccessGateway;
import com.github.lye.access.AccessResolver;
import com.github.lye.access.DefaultAccessResolver;
import com.github.lye.access.rules.CollectFirstRule;
import com.github.lye.commands.LoanCommand;
import com.github.lye.commands.MarketCommand;
import com.github.lye.commands.SellCommand;
import com.github.lye.commands.TradeFlowAdminCommand;
import com.github.lye.commands.TradeFlowCommand;
import com.github.lye.commands.core.CommandManager;
import com.github.lye.config.Config;
import com.github.lye.config.ConfigResolver;
import com.github.lye.data.*;
import com.github.lye.database.IPlayerCollectionData;
import com.github.lye.database.IServerCollectionData;
import com.github.lye.error.TradeFlowExceptionHandler;
import com.github.lye.events.ChestSellSelector;
import com.github.lye.events.EconomicEventManager;
import com.github.lye.events.PlayerCollectionListener;
import com.github.lye.events.PlayerConnectionListener;
import com.github.lye.events.TradeFlowInventoryCheckEvent;
import com.github.lye.gui.GuiNavigator;
import com.github.lye.gui.NavigationHistory;
import com.github.lye.gui.TransactionLock;
import com.github.lye.events.RumorInteractListener;
import com.github.lye.gameplay.ReputationManager;
import com.github.lye.repository.ServerStateRepository;
import com.github.lye.repository.FileServerStateRepository;
import com.github.lye.gameplay.rumors.RumorManager;
import com.github.lye.gmq.GmqService;
import com.github.lye.market.MarketTrendManager;
import com.github.lye.market.StockManager;
import com.github.lye.pricing.PricingManager;
import com.github.lye.pricing.gui.FamilyRegistry;
import com.github.lye.pricing.service.PriceService;
import com.github.lye.redis.BalanceSyncManager;
import com.github.lye.redis.ClusterSyncManager;
import com.github.lye.redis.RedissonRedisClient;
import com.github.lye.redis.NoOpRedisClient;
import com.github.lye.redis.RedisClient;
import com.github.lye.redis.TransactionSyncManager;
import com.github.lye.registry.ServiceRegistry;
import com.github.lye.service.*;
import com.github.lye.service.impl.DefaultInventoryService;
import com.github.lye.service.impl.DefaultMessageService;
import com.github.lye.service.impl.DefaultPurchaseValidationService;
import com.github.lye.service.impl.DefaultTransactionService;
import com.github.lye.util.EconomyUtil;
import com.github.lye.util.TradeFlowLogger;
import com.github.lye.config.settings.*;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Main bootstrap service for TradeFlow plugin.
 * <p>
 * This class orchestrates the entire plugin initialization process,
 * managing the creation and wiring of all services, components, and systems.
 * It replaces the monolithic initialization code from the main plugin class.</p>
 *
 * @author  lye
 * @since   0.1
 */
public class PluginBootstrap {

    private final TradeFlow plugin;
    private final TradeFlowLogger logger;
    private final TradeFlowExceptionHandler exceptionHandler;
    private final ServiceRegistry serviceRegistry;

    // Bootstrap services
    private ConfigLoaderService configLoader;
    private DatabaseBootstrapService databaseBootstrap;
    private PricingBootstrapService pricingBootstrap;
    private SchedulerService schedulerService;

    // Core components
    private AccessGateway accessGateway;
    private ConfigResolver configResolver;
    private AccessResolver accessResolver;
    private Database database;
    private ServerStateRepository serverStateRepository;

    // Collections
    private final Map<UUID, Set<String>> loadedAutosellSettings = new ConcurrentHashMap<>();

    // Redis client
    private RedisClient redisClient;
    private String redisHost = "N/A";
    private int redisPort = 0;

    /**
     * Creates a new plugin bootstrap.
     *
     * @param plugin the plugin instance
     */
    public PluginBootstrap(TradeFlow plugin) {
        this.plugin = plugin;
        this.logger = plugin.getTradeLogger();
        this.exceptionHandler = new TradeFlowExceptionHandler(logger, plugin);
        this.serviceRegistry = new ServiceRegistry();
        this.serviceRegistry.register(TradeFlowLogger.class, logger);
    }

    /**
     * Runs the complete bootstrap process.
     */
    public void run() {
        try {
            logStartupBanner();

            // Step 1: Setup economy
            EconomyUtil.setupLocalEconomy(Bukkit.getServer());

            // Step 2: Load configuration
            configLoader = new ConfigLoaderService(plugin);
            configLoader.loadAll();
            registerConfigSettings();

            // Step 3: Initialize database
            databaseBootstrap = new DatabaseBootstrapService(
                plugin,
                configLoader.getPluginSettings(),
                exceptionHandler
            );
            databaseBootstrap.initialize(
                this::finishMySQLBootstrap,
                this::finishFileBootstrap
            );

        } catch (Exception e) {
            logger.severe("Failed to bootstrap plugin: " + e.getMessage(), e);
            Bukkit.getPluginManager().disablePlugin(plugin);
        }
    }

    /**
     * Logs the startup banner.
     */
    private void logStartupBanner() {
        // Banner will be displayed at the end with full info (MySQL, Redis status)
        StartupBanner.displayLoadingBoxStart();
        StartupBanner.displayStep("SYSTÈME", "Initialisation du noyau...");
    }

    /**
     * Registers all configuration settings in the service registry.
     */
    private void registerConfigSettings() {
        serviceRegistry
            .register(IPluginSettings.class, configLoader.getPluginSettings())
            .register(IPricingSettings.class, configLoader.getPricingSettings())
            .register(IGuiSettings.class, configLoader.getGuiSettings())
            .register(IMessageSettings.class, configLoader.getMessageSettings())
            .register(IShopDefinitions.class, configLoader.getShopDefinitions())
            .register(IAutosellSettings.class, configLoader.getAutosellSettings())
            .register(IEconomicEventSettings.class, configLoader.getEconomicEventSettings())
            .register(ITaxSettings.class, configLoader.getTaxSettings());
    }

    /**
     * Completes bootstrap after MySQL connection is established.
     */
    private void finishMySQLBootstrap() {
        StartupBanner.displayStep("SQL", "Connexion MySQL établie");
        finishBootstrap(true);
    }

    /**
     * Completes bootstrap after falling back to file storage.
     */
    private void finishFileBootstrap() {
        StartupBanner.displayStep("DATA", "Stockage local activé");
        finishBootstrap(false);
    }

    /**
     * Completes the bootstrap process after storage is ready.
     *
     * @param mysqlEnabled true if MySQL is enabled
     */
    private void finishBootstrap(boolean mysqlEnabled) {
        // Init ServerStateRepository based on storage mode
        if (mysqlEnabled && databaseBootstrap.getServerStateData() != null) {
            this.serverStateRepository = databaseBootstrap.getServerStateData();
        } else {
            this.serverStateRepository = new FileServerStateRepository(plugin);
        }
        
        // Display progress steps
        StartupBanner.displayStep("CONFIG", "Chargement des modules YAML...");

        // 1. Core Services
        initializeAccessControl();
        initializeDatabase();

        StartupBanner.displayStep("CORE", "Services de base initialisés");

        // 2. Central Bank & Pricing
        initializeCentralBank();
        pricingBootstrap = new PricingBootstrapService(
            plugin,
            serviceRegistry.get(CentralBankStockManager.class),
            database,
            databaseBootstrap.getPriceDatabaseAPI(),
            configLoader.getPricingSettings(),
            configLoader.getPluginSettings()
        );
        pricingBootstrap.initialize();

        StartupBanner.displayStep("PRICING", "Système de prix initialisé");

        // 3. Redis
        initializeRedis();

        // 3.5. Cross-Server Sync Managers
        initializeSyncManagers();

        // 4. Mark storage ready
        accessGateway.markStorageReady();
        accessGateway.warmFromDatabase();

        // 5. Business Services
        initializeBusinessServices();

        StartupBanner.displayStep("SERVICES", "Services métier initialisés");

        // 6. Market features
        initializeMarketFeatures();

        // Sync legacy fields BEFORE creating listeners/commands that depend on them
        plugin.syncLegacyFields();

        // Initialize Format's message settings for legacy static message resolution
        com.github.lye.util.Format.setMessageSettings(configLoader.getMessageSettings());

        // 7. Events and Commands
        setupEvents();
        setupCommands();

        // Display final info box
        int shopCount = database.getShops().size();
        double reserve = serviceRegistry.get(CentralBankStockManager.class).getMonetaryReserve();

        StartupBanner.displayStep("STATS", shopCount + " articles chargés");
        StartupBanner.displayLoadingBoxEnd(shopCount, reserve);

        // Display the full system banner with all info
        StartupBanner banner = new StartupBanner(plugin)
                .withMySQL(mysqlEnabled)
                .withRedis(redisClient.isEnabled(), redisHost, redisPort);
        banner.display();
    }

    /**
     * Initializes access control system.
     */
    private void initializeAccessControl() {
        IPlayerCollectionData playerCollectionData = databaseBootstrap.getPlayerCollectionData();
        IServerCollectionData serverCollectionData = databaseBootstrap.getServerCollectionData();

        this.accessGateway = new AccessGateway(plugin, playerCollectionData, serverCollectionData);
        this.configResolver = new ConfigResolver(Config.getConfigConfig(), Config.getShopsConfig());

        List<com.github.lye.access.AccessRule> rules = new ArrayList<>();
        rules.add(new CollectFirstRule());
        this.accessResolver = new DefaultAccessResolver(
            rules,
            accessGateway::isAccessReady,
            accessGateway,
            configResolver
        );

        serviceRegistry
            .register(AccessGateway.class, accessGateway)
            .register(ConfigResolver.class, configResolver)
            .register(AccessResolver.class, accessResolver);
    }

    /**
     * Initializes the main database.
     */
    private void initializeDatabase() {
        this.database = new Database(logger, configLoader.getPluginSettings());
        this.database.initialize(
            plugin,
            configLoader.getShopDefinitions(),
            configLoader.getPricingSettings(),
            configLoader.getPluginSettings()
        );

        serviceRegistry
            .register(Database.class, database)
            .register(ShopUtil.class, database.getShopUtil());

        EconomyDataUtil economyDataUtil = new EconomyDataUtil(database, database.getEconomyData());
        serviceRegistry.register(EconomyDataUtil.class, economyDataUtil);
    }

    /**
     * Initializes the central bank system.
     */
    private void initializeCentralBank() {
        CentralBankStockManager centralBankStockManager = new CentralBankStockManager(
            plugin,
            databaseBootstrap.isMySqlEnabled() ? databaseBootstrap.getGlobalStockData() : null,
            database,
            configLoader.getPluginSettings(),
            configLoader.getPricingSettings()
        );
        serviceRegistry.register(CentralBankStockManager.class, centralBankStockManager);
    }

    /**
     * Initializes Redis client.
     */
    private void initializeRedis() {
        boolean redisEnabled = plugin.getConfig().getBoolean("redis.enabled", false);

        if (!redisEnabled) {
            StartupBanner.displayStep("REDIS", "Désactivé (mode standalone)");
            logger.info("Redis is disabled in config, using NoOpRedisClient");
            this.redisClient = new NoOpRedisClient();
        } else {
            String host = plugin.getConfig().getString("redis.single.host",
                          plugin.getConfig().getString("redis.host", "localhost"));
            int port = plugin.getConfig().getInt("redis.single.port",
                       plugin.getConfig().getInt("redis.port", 6379));
            String password = plugin.getConfig().getString("redis.auth.password",
                              plugin.getConfig().getString("redis.password", ""));
            int database = plugin.getConfig().getInt("redis.auth.database",
                       plugin.getConfig().getInt("redis.database", 0));

            try {
                this.redisHost = host;
                this.redisPort = port;
                this.redisClient = new RedissonRedisClient(host, port, password, database, true);
                logger.info("Redis client initialized: " + host + ":" + port + " (DB: " + database + ")");

                if (redisClient.isEnabled()) {
                    StartupBanner.displayStep("REDIS", "Connexion établie " + host + ":" + port);
                    logger.info("Redis connection test successful - cross-server sync enabled");
                } else {
                    StartupBanner.displayWarning("Redis connexion failed - using NoOp");
                    logger.warning("Redis connection test failed, falling back to NoOpRedisClient");
                    this.redisClient = new NoOpRedisClient();
                }
            } catch (Exception e) {
                StartupBanner.displayError("Redis initialization failed: " + e.getMessage());
                logger.warning("Failed to initialize Redis client: " + e.getMessage());
                logger.warning("Falling back to NoOpRedisClient - cross-server features disabled");
                this.redisClient = new NoOpRedisClient();
            }
        }
        serviceRegistry.register(RedisClient.class, redisClient);
    }

    /**
     * Initializes cross-server synchronization managers.
     */
    private void initializeSyncManagers() {
        if (!redisClient.isEnabled()) {
            StartupBanner.displayStep("SYNC", "Désactivé (mode standalone)");
            return;
        }

        // Balance Sync Manager
        BalanceSyncManager balanceSyncManager = new BalanceSyncManager(plugin, redisClient);
        serviceRegistry.register(BalanceSyncManager.class, balanceSyncManager);

        // Transaction Sync Manager
        TransactionSyncManager transactionSyncManager = new TransactionSyncManager(plugin, redisClient);
        serviceRegistry.register(TransactionSyncManager.class, transactionSyncManager);

        // Cluster Sync Manager — heartbeat, leader election, state coordination
        ClusterSyncManager clusterSyncManager = new ClusterSyncManager(plugin, redisClient);
        clusterSyncManager.startListening();
        serviceRegistry.register(ClusterSyncManager.class, clusterSyncManager);

        StartupBanner.displayStep("SYNC", "Cross-server sync enabled");
        logger.info("Cross-server sync managers initialized: balance, transaction, cluster");
    }

    /**
     * Initializes business services.
     */
    private void initializeBusinessServices() {
        IMessageSettings messageSettings = configLoader.getMessageSettings();
        IMessageService messageService = new DefaultMessageService(messageSettings);
        serviceRegistry.register(IMessageService.class, messageService);

        IPluginSettings pluginSettings = configLoader.getPluginSettings();
        IEconomicEventSettings economicEventSettings = configLoader.getEconomicEventSettings();

        EconomicEventManager economicEventManager = new EconomicEventManager(
            plugin,
            this.serverStateRepository,
            economicEventSettings,
            configLoader.getPricingSettings(),
            redisClient
        );
        serviceRegistry.register(EconomicEventManager.class, economicEventManager);

        CentralBankStockManager centralBankStockManager = serviceRegistry.get(CentralBankStockManager.class);

        GmqService gmqService = new GmqService(plugin, database.getGmqRepository(), logger, centralBankStockManager);
        serviceRegistry.register(GmqService.class, gmqService);

        ReputationManager reputationManager = new ReputationManager(plugin);
        serviceRegistry.register(ReputationManager.class, reputationManager);

        RumorManager rumorManager = new RumorManager(plugin);
        serviceRegistry.register(RumorManager.class, rumorManager);

        com.github.lye.license.LicenseManager licenseManager = new com.github.lye.license.LicenseManager(
            plugin, database.getLicenseRepository()
        );
        serviceRegistry.register(com.github.lye.license.LicenseManager.class, licenseManager);

        // Initialize Tax Manager
        TaxManager taxManager = new TaxManager(plugin, configLoader.getTaxSettings(), database);
        serviceRegistry.register(TaxManager.class, taxManager);

        ShopUtil shopUtil = serviceRegistry.get(ShopUtil.class);

        IPurchaseValidationService purchaseValidationService = new DefaultPurchaseValidationService(
            database, messageSettings, pluginSettings, configLoader.getPricingSettings(), messageService, centralBankStockManager, shopUtil, plugin
        );
        serviceRegistry.register(IPurchaseValidationService.class, purchaseValidationService);

        ITransactionService transactionService = new DefaultTransactionService(
            plugin, database, serviceRegistry.get(EconomyDataUtil.class), shopUtil,
            centralBankStockManager, gmqService, redisClient
        );
        serviceRegistry.register(ITransactionService.class, transactionService);

        IInventoryService inventoryService = new DefaultInventoryService(messageSettings, messageService, plugin);
        serviceRegistry.register(IInventoryService.class, inventoryService);

        // Async execution service (Java 21 virtual threads)
        AsyncExecutor asyncExecutor = new AsyncExecutor(plugin);
        serviceRegistry.register(AsyncExecutor.class, asyncExecutor);

        TradePricingService pricingService = new TradePricingService(centralBankStockManager, licenseManager, reputationManager);
        TradeEconomyService economyService = new TradeEconomyService(centralBankStockManager, Config.get(), EconomyUtil.getEconomy());

        TradeExecutionService executionService = new TradeExecutionService(
            plugin, asyncExecutor,
            database, shopUtil, centralBankStockManager,
            purchaseValidationService, transactionService, inventoryService, messageService,
            pricingService, economyService,
            licenseManager, reputationManager, taxManager,
            EconomyUtil.getEconomy(), Config.get()
        );
        serviceRegistry.register(TradeExecutionService.class, executionService);

        // Register pricing services
        serviceRegistry
            .register(PriceService.class, pricingBootstrap.getPriceService())
            .register(FamilyRegistry.class, pricingBootstrap.getFamilyRegistry())
            .register(PricingManager.class, pricingBootstrap.getPricingManager());
    }

    /**
     * Initializes market-related features.
     */
    private void initializeMarketFeatures() {
        StockManager stockManager = new StockManager(
            plugin,
            this.serverStateRepository
        );
        serviceRegistry.register(StockManager.class, stockManager);

        // MarketTrendManager requires ServerStateRepository
        MarketTrendManager marketTrendManager = new MarketTrendManager(plugin, this.serverStateRepository);
        serviceRegistry.register(MarketTrendManager.class, marketTrendManager);

        GuiNavigator guiNavigator = new GuiNavigator(plugin);
        serviceRegistry.register(GuiNavigator.class, guiNavigator);
    }

    /**
     * Sets up event listeners.
     */
    private void setupEvents() {
        new PlayerCollectionListener(plugin, accessGateway, configResolver);
        new ChestSellSelector(plugin, database, serviceRegistry.get(TradeExecutionService.class),
            serviceRegistry.get(IMessageService.class));
        new PlayerConnectionListener(plugin, accessGateway);
        plugin.getServer().getPluginManager().registerEvents(new RumorInteractListener(plugin), plugin);
    }

    /**
     * Sets up commands and scheduled tasks.
     * <p>
     * Commands are registered via Paper's Brigadier lifecycle
     * ({@link io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents#COMMANDS}),
     * delegating to the existing {@link CommandManager} for subcommand dispatch.
     */
    private void setupCommands() {
        // Create AdminNavigator for admin GUI navigation
        com.github.lye.gui.AdminNavigator adminNavigator = new com.github.lye.gui.AdminNavigator(plugin);

        // Build the CommandManager with all subcommands
        CommandManager cm = new CommandManager(plugin);
        cm.registerCommand(new com.github.lye.commands.RumorCommand(plugin));
        cm.registerCommand(new com.github.lye.commands.BlackMarketCommand(plugin));
        cm.registerCommand(new com.github.lye.commands.LicenseCommand(plugin));
        cm.registerCommand(new com.github.lye.commands.admin.TaxCommand(plugin));
        cm.registerCommand(new TradeFlowCommand(plugin));
        cm.registerCommand(new TradeFlowAdminCommand(plugin, adminNavigator));

        // Create standalone command instances (they are also subcommands of /tradeflow)
        SellCommand sellCommand = new SellCommand(plugin);
        MarketCommand marketCommand = new MarketCommand(plugin);

        // Register all commands via Paper's Brigadier API
        com.github.lye.commands.core.BrigadierRegistry.register(plugin, cm, sellCommand, marketCommand);

        // Start scheduler
        schedulerService = new SchedulerService(
            plugin,
            database,
            configLoader.getPluginSettings(),
            serviceRegistry.get(EconomicEventManager.class),
            serviceRegistry.get(CentralBankStockManager.class),
            serviceRegistry.get(PricingManager.class),
            serviceRegistry.get(StockManager.class),
            serviceRegistry.get(com.github.lye.data.EconomyDataUtil.class)
        );
        schedulerService.start();
    }

    /**
     * Shuts down all services and components.
     */
    public void shutdown() {
        logger.info("Shutting down TradeFlow...");

        // Save market trends to file before shutting down
        MarketTrendManager marketTrendManager = serviceRegistry.get(MarketTrendManager.class);
        if (marketTrendManager != null) {
            marketTrendManager.shutdown();
        }

        // Shut down cluster sync before closing Redis
        ClusterSyncManager clusterSyncManager = serviceRegistry.get(ClusterSyncManager.class);
        if (clusterSyncManager != null) {
            clusterSyncManager.shutdown();
        }

        TransactionSyncManager transactionSyncManager = serviceRegistry.get(TransactionSyncManager.class);
        if (transactionSyncManager != null) {
            transactionSyncManager.shutdown();
        }

        BalanceSyncManager balanceSyncManager = serviceRegistry.get(BalanceSyncManager.class);
        if (balanceSyncManager != null) {
            balanceSyncManager.shutdown();
        }

        if (schedulerService != null) {
            schedulerService.shutdown();
        }

        if (pricingBootstrap != null) {
            pricingBootstrap.shutdown();
        }

        // Shut down async executor (waits for in-flight virtual thread tasks)
        AsyncExecutor asyncExecutor = serviceRegistry.get(AsyncExecutor.class);
        if (asyncExecutor != null) {
            asyncExecutor.shutdown();
        }

        if (databaseBootstrap != null) {
            databaseBootstrap.shutdown();
        }

        if (redisClient != null) {
            redisClient.close();
        }

        TaxManager taxManager = serviceRegistry.get(TaxManager.class);
        if (taxManager != null) {
            taxManager.clearAll();
        }

        TransactionLock.clearAll();
        NavigationHistory.clearAll();
        TradeExecutionService.clearAll();
        TradeFlowInventoryCheckEvent.clearAll();
        ChestSellSelector.clearAll();

        serviceRegistry.clear();

        StartupBanner.displayShutdown();
        logger.info("TradeFlow shutdown complete");
    }

    /**
     * Gets the service registry.
     *
     * @return the service registry
     */
    public ServiceRegistry getServiceRegistry() {
        return serviceRegistry;
    }

    /**
     * Gets the config loader service.
     *
     * @return the config loader
     */
    public ConfigLoaderService getConfigLoader() {
        return configLoader;
    }

    /**
     * Gets the database bootstrap service.
     *
     * @return the database bootstrap
     */
    public DatabaseBootstrapService getDatabaseBootstrap() {
        return databaseBootstrap;
    }

    /**
     * Gets the pricing bootstrap service.
     *
     * @return the pricing bootstrap
     */
    public PricingBootstrapService getPricingBootstrap() {
        return pricingBootstrap;
    }

    /**
     * Gets the scheduler service.
     *
     * @return the scheduler service
     */
    public SchedulerService getSchedulerService() {
        return schedulerService;
    }

    /**
     * Gets the access gateway.
     *
     * @return the access gateway
     */
    public AccessGateway getAccessGateway() {
        return accessGateway;
    }

    /**
     * Gets the database.
     *
     * @return the database
     */
    public Database getDatabase() {
        return database;
    }

    /**
     * Gets the Redis client.
     *
     * @return the Redis client
     */
    public RedisClient getRedisClient() {
        return redisClient;
    }

    /**
     * Gets the loaded autosell settings.
     *
     * @return the autosell settings map
     */
    public Map<UUID, Set<String>> getLoadedAutosellSettings() {
        return loadedAutosellSettings;
    }
}
