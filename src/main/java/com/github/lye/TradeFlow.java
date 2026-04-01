package com.github.lye;

import com.github.lye.bootstrap.PluginBootstrap;
import com.github.lye.config.Config;
import com.github.lye.config.ConfigResolver;
import com.github.lye.config.settings.*;
import com.github.lye.access.AccessResolver;
import com.github.lye.data.*;
import com.github.lye.database.*;
import com.github.lye.events.EconomicEventManager;
import com.github.lye.gameplay.ReputationManager;
import com.github.lye.gameplay.rumors.RumorManager;
import com.github.lye.gateway.AccessGateway;
import com.github.lye.gui.GuiNavigator;
import com.github.lye.gmq.GmqService;
import com.github.lye.license.LicenseManager;
import com.github.lye.market.MarketTrendManager;
import com.github.lye.market.StockManager;
import com.github.lye.pricing.PricingManager;
import com.github.lye.pricing.database.PriceDatabaseAPI;
import com.github.lye.pricing.gui.FamilyRegistry;
import com.github.lye.pricing.service.PriceService;
import com.github.lye.redis.RedisClient;
import com.github.lye.repository.MySQLShopRepository;
import com.github.lye.repository.PriceRepository;
import com.github.lye.registry.ServiceRegistry;
import com.github.lye.service.*;
import com.github.lye.util.EconomyUtil;
import com.github.lye.util.TradeFlowLogger;
import lombok.Getter;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main plugin class for TradeFlow.
 * <p>
 * TradeFlow is a comprehensive economic system for Minecraft servers featuring:
 * <ul>
 *   <li>Dynamic pricing based on supply and demand</li>
 *   <li>Central bank management</li>
 *   <li>Player loans and credit system</li>
 *   <li>Market trends and economic events</li>
 *   <li>GUI-based shop interface</li>
 * </ul>
 *
 * <p>This plugin uses a service-oriented architecture with dependency injection
 * managed by {@link ServiceRegistry}. The initialization process is handled
 * by {@link PluginBootstrap}.</p>
 *
 * @author  lye
 * @since   0.1
 * @see     PluginBootstrap
 * @see     ServiceRegistry
 */
public class TradeFlow extends JavaPlugin {

    private static volatile TradeFlow instance;
    private PluginBootstrap bootstrap;

    // Legacy field references for backward compatibility
    private LocalDate lastResetDate;
    private MySQLConnector mysqlConnector;
    private BatchWriteOptimizer batchWriteOptimizer;
    private boolean mySqlEnabled = false;
    private MySQLShopRepository mySQLShopRepository;
    private PlayerData playerData;
    private com.github.lye.database.GlobalStockData globalStockData;
    private com.github.lye.database.ServerStateData serverStateData;
    private IPlayerCollectionData playerCollectionData;
    private IServerCollectionData serverCollectionData;
    private final Map<UUID, Set<String>> loadedAutosellSettings = new ConcurrentHashMap<>();
    private EconomicEventManager economicEventManager;
    private CentralBankStockManager centralBankStockManager;
    private GmqService gmqService;
    private List<String> sortedShopItems;
    private AccessGateway accessGateway;
    private ConfigResolver configResolver;
    private AccessResolver accessResolver;
    private PriceService priceService;
    private FamilyRegistry familyRegistry;
    private IPurchaseValidationService purchaseValidationService;
    private ITransactionService transactionService;
    private IInventoryService inventoryService;
    private IMessageService messageService;
    private PurchaseUtil purchaseUtil;
    private IPluginSettings pluginSettings;
    private IPricingSettings pricingSettings;
    private IGuiSettings guiSettings;
    private IMessageSettings messageSettings;
    private IShopDefinitions shopDefinitions;
    private IAutosellSettings autosellSettings;
    private IEconomicEventSettings economicEventSettings;
    private EconomyDataUtil economyDataUtil;
    private ShopUtil shopUtil;
    private MarketTrendManager marketTrendManager;
    private StockManager stockManager;
    private RumorManager rumorManager;
    private ReputationManager reputationManager;
    private Database database;
    private LicenseManager licenseManager;
    private GuiNavigator guiNavigator;
    private PriceDatabaseAPI priceDatabaseAPI;
    private com.github.lye.repository.PriceRepository priceRepository;
    private PricingManager pricingManager;
    private TradeFlowLogger tradeLogger;
    private RedisClient redisClient;
    private TaxManager taxManager;

    /**
     * Gets the singleton instance of the plugin.
     *
     * @return the TradeFlow instance
     */
    public static TradeFlow getInstance() {
        return instance;
    }

    /**
     * Gets the service registry for accessing all plugin services.
     *
     * @return the service registry
     */
    public ServiceRegistry getServices() {
        return bootstrap != null ? bootstrap.getServiceRegistry() : null;
    }

    public PluginBootstrap getBootstrap() {
        return bootstrap;
    }

    @Override
    public void onEnable() {
        instance = this;
        this.tradeLogger = new TradeFlowLogger(this);

        // Initialize the static logger in Format for legacy code
        com.github.lye.util.Format.setLog(this.tradeLogger);

        // Run the bootstrap process
        this.bootstrap = new PluginBootstrap(this);
        this.bootstrap.run();

        // If bootstrap disabled the plugin or config is unavailable, stop here.
        if (!isEnabled() || bootstrap.getConfigLoader() == null || bootstrap.getConfigLoader().getPluginSettings() == null) {
            this.tradeLogger.severe("Bootstrap did not complete successfully; skipping post-bootstrap initialization.");
            return;
        }

        // Note: syncLegacyFields() will be called by bootstrap when services are ready
        // Initialize local server
        com.github.lye.server.LocalServer.initialize(
            this,
            bootstrap.getConfigLoader().getPluginSettings(),
            this.tradeLogger
        );
    }

    @Override
    public void onDisable() {
        if (bootstrap != null) {
            bootstrap.shutdown();
        }
        instance = null;
    }

    /**
     * Synchronizes legacy fields with the service registry.
     * <p>
     * This method maintains backward compatibility with existing code
     * that accesses fields directly. New code should use the ServiceRegistry.</p>
     */
    public void syncLegacyFields() {
        if (bootstrap == null) {
            return;
        }

        ServiceRegistry services = bootstrap.getServiceRegistry();
        if (services == null) {
            return;
        }

        this.pluginSettings = services.get(IPluginSettings.class);
        this.pricingSettings = services.get(IPricingSettings.class);
        this.guiSettings = services.get(IGuiSettings.class);
        this.messageSettings = services.get(IMessageSettings.class);
        this.shopDefinitions = services.get(IShopDefinitions.class);
        this.autosellSettings = services.get(IAutosellSettings.class);
        this.economicEventSettings = services.get(IEconomicEventSettings.class);

        this.database = services.get(Database.class);
        this.shopUtil = services.get(ShopUtil.class);
        this.economyDataUtil = services.get(EconomyDataUtil.class);
        this.centralBankStockManager = services.get(CentralBankStockManager.class);
        this.priceService = services.get(PriceService.class);
        this.familyRegistry = services.get(FamilyRegistry.class);
        this.purchaseValidationService = services.get(IPurchaseValidationService.class);
        this.transactionService = services.get(ITransactionService.class);
        this.inventoryService = services.get(IInventoryService.class);
        this.messageService = services.get(IMessageService.class);
        this.purchaseUtil = services.get(PurchaseUtil.class);
        this.economicEventManager = services.get(EconomicEventManager.class);
        this.gmqService = services.get(GmqService.class);
        this.reputationManager = services.get(ReputationManager.class);
        this.rumorManager = services.get(RumorManager.class);
        this.licenseManager = services.get(LicenseManager.class);
        this.stockManager = services.get(StockManager.class);
        this.marketTrendManager = services.get(MarketTrendManager.class);
        this.guiNavigator = services.get(GuiNavigator.class);
        this.pricingManager = services.get(PricingManager.class);
        this.taxManager = services.get(TaxManager.class);
        this.configResolver = services.get(ConfigResolver.class);
        this.accessResolver = services.get(AccessResolver.class);
        this.accessGateway = bootstrap.getAccessGateway();

        this.mysqlConnector = bootstrap.getDatabaseBootstrap().getMysqlConnector();
        this.mySqlEnabled = bootstrap.getDatabaseBootstrap().isMySqlEnabled();
        this.batchWriteOptimizer = bootstrap.getDatabaseBootstrap().getBatchWriteOptimizer();
        this.mySQLShopRepository = bootstrap.getDatabaseBootstrap().getMySQLShopRepository();
        this.playerData = bootstrap.getDatabaseBootstrap().getPlayerData();
        this.globalStockData = bootstrap.getDatabaseBootstrap().getGlobalStockData();
        this.serverStateData = bootstrap.getDatabaseBootstrap().getServerStateData();
        this.playerCollectionData = bootstrap.getDatabaseBootstrap().getPlayerCollectionData();
        this.serverCollectionData = bootstrap.getDatabaseBootstrap().getServerCollectionData();
        this.priceDatabaseAPI = bootstrap.getDatabaseBootstrap().getPriceDatabaseAPI();

        this.redisClient = bootstrap.getRedisClient();

        if (bootstrap.getSchedulerService() != null) {
            this.lastResetDate = bootstrap.getSchedulerService().getLastResetDate();
        }
    }

    /**
     * Requests a recalculation of all prices.
     */
    public void recalculatePrices() {
        if (bootstrap != null && bootstrap.getPricingBootstrap() != null) {
            bootstrap.getPricingBootstrap().recalculatePrices();
        }
    }

    /**
     * Runs a task synchronously on the global region.
     *
     * @param runnable the task to run
     */
    public static void runSync(TradeFlow plugin, Runnable runnable) {
        com.github.lye.util.FoliaSchedulers.runGlobal(plugin, runnable);
    }

    // ==================== LEGACY GETTERS ====================
    // These methods maintain backward compatibility with existing code

    public PriceService getPriceService() { return priceService; }
    public PricingManager getPricingManager() { return pricingManager; }
    public FamilyRegistry getFamilyRegistry() { return familyRegistry; }
    public CentralBankStockManager getCentralBankStockManager() { return centralBankStockManager; }
    public ReputationManager getReputationManager() { return reputationManager; }
    public IPurchaseValidationService getPurchaseValidationService() { return purchaseValidationService; }
    public ITransactionService getTransactionService() { return transactionService; }
    public IInventoryService getInventoryService() { return inventoryService; }
    public IMessageService getMessageService() { return messageService; }
    public PurchaseUtil getPurchaseUtil() { return purchaseUtil; }
    public IPluginSettings getPluginSettings() { return pluginSettings; }
    public Database getDatabase() { return database; }
    public Economy getEconomy() { return EconomyUtil.getEconomy(); }
    public Map<String, Transaction> getLoadedTransactions() {
        return database != null ? database.transactions : new ConcurrentHashMap<>();
    }
    public Map<String, Shop> getLoadedShops() {
        return database != null ? database.getShops() : new ConcurrentHashMap<>();
    }
    public Map<String, Loan> getLoadedLoans() {
        return database != null ? database.getLoans() : new ConcurrentHashMap<>();
    }
    public Map<String, double[]> getLoadedEconomyData() {
        return database != null ? database.economyData : new ConcurrentHashMap<>();
    }
    public PlayerData getPlayerData() { return playerData; }
    public Map<UUID, Set<String>> getLoadedAutosellSettings() { return loadedAutosellSettings; }
    public boolean isMySqlEnabled() { return mySqlEnabled; }
    public MySQLConnector getMysqlConnector() { return mysqlConnector; }
    public BatchWriteOptimizer getBatchWriteOptimizer() { return batchWriteOptimizer; }
    public MySQLShopRepository getMySQLShopRepository() { return mySQLShopRepository; }
    public EconomicEventManager getEconomicEventManager() { return economicEventManager; }
    public AccessGateway getAccessGateway() { return accessGateway; }
    public ConfigResolver getConfigResolver() { return configResolver; }
    public AccessResolver getAccessResolver() { return accessResolver; }
    public ServerStateData getServerStateData() { return serverStateData; }
    public TradeFlowLogger getTradeLogger() { return tradeLogger; }
    public RedisClient getRedisClient() { return redisClient; }
    public IShopDefinitions getShopDefinitions() { return shopDefinitions; }
    public IEconomicEventSettings getEconomicEventSettings() { return economicEventSettings; }
    public EconomyDataUtil getEconomyDataUtil() { return economyDataUtil; }
    public ShopUtil getShopUtil() { return shopUtil; }
    public IPlayerCollectionData getPlayerCollectionData() { return playerCollectionData; }
    public GuiNavigator getGuiNavigator() { return guiNavigator; }
    public RumorManager getRumorManager() { return rumorManager; }
    public IGuiSettings getGuiSettings() { return guiSettings; }
    public IMessageSettings getMessageSettings() { return messageSettings; }
    public IPricingSettings getPricingSettings() { return pricingSettings; }
    public MarketTrendManager getMarketTrendManager() { return marketTrendManager; }
    public LicenseManager getLicenseManager() { return licenseManager; }
    public IAutosellSettings getAutosellSettings() { return autosellSettings; }
    public TaxManager getTaxManager() { return taxManager; }
    public ITaxSettings getTaxSettings() {
        return bootstrap != null ? bootstrap.getConfigLoader().getTaxSettings() : null;
    }
}
