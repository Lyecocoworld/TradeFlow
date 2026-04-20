package com.github.lye.bootstrap;

import com.github.lye.TradeFlow;
import com.github.lye.database.BatchWriteOptimizer;
import com.github.lye.database.FilePlayerCollectionData;
import com.github.lye.database.FileServerCollectionData;
import com.github.lye.database.IPlayerCollectionData;
import com.github.lye.database.IServerCollectionData;
import com.github.lye.database.MySQLConnector;
import com.github.lye.database.PlayerCollectionData;
import com.github.lye.database.ServerCollectionData;
import com.github.lye.database.GlobalStockData;
import com.github.lye.database.ServerStateData;
import com.github.lye.error.DatabaseException;
import com.github.lye.error.TradeFlowExceptionHandler;
import com.github.lye.pricing.database.MySQLPriceDatabaseAPIImpl;
import com.github.lye.pricing.database.PriceDatabaseAPI;
import com.github.lye.repository.MySQLShopRepository;
import com.github.lye.config.settings.IPluginSettings;

import com.github.lye.util.TradeFlowLogger;

import java.sql.SQLException;
import java.util.function.Consumer;

/**
 * Service responsible for bootstrapping the database layer.
 * <p>
 * This service handles MySQL connection with fallback to file-based storage,
 * creates necessary tables, and initializes repositories.</p>
 *
 * @author  lye
 * @since   0.1
 */
public class DatabaseBootstrapService {

    private final TradeFlow plugin;
    private final IPluginSettings pluginSettings;
    private final TradeFlowExceptionHandler exceptionHandler;
    private final TradeFlowLogger tradeLogger;

    // Initialized components
    private MySQLConnector mysqlConnector;
    private BatchWriteOptimizer batchWriteOptimizer;
    private boolean mySqlEnabled = false;
    private MySQLShopRepository mySQLShopRepository;
    private com.github.lye.database.PlayerData playerData;
    private GlobalStockData globalStockData;
    private ServerStateData serverStateData;
    private IPlayerCollectionData playerCollectionData;
    private IServerCollectionData serverCollectionData;
    private PriceDatabaseAPI priceDatabaseAPI;

    /**
     * Creates a new database bootstrap service.
     *
     * @param plugin         the plugin instance
     * @param pluginSettings the plugin settings
     * @param exceptionHandler the exception handler
     */
    public DatabaseBootstrapService(TradeFlow plugin,
                                    IPluginSettings pluginSettings,
                                    TradeFlowExceptionHandler exceptionHandler) {
        this.plugin = plugin;
        this.pluginSettings = pluginSettings;
        this.exceptionHandler = exceptionHandler;
        this.tradeLogger = plugin.getServices().get(TradeFlowLogger.class);
    }

    /**
     * Initializes the database connection and creates tables.
     *
     * @param onSuccess callback to run when database is ready (on main thread)
     * @param onFallback callback to run when falling back to file storage
     */
    public void initialize(Runnable onSuccess, Runnable onFallback) {
        if (!pluginSettings.isDatabaseEnabled()) {
            tradeLogger.config("Database not enabled, using file storage");
            initializeFileStorage();
            if (onFallback != null) {
                onFallback.run();
            }
            return;
        }

        tradeLogger.config("Connecting to MySQL...");
        connectToMySQL(onSuccess, onFallback);
    }

    /**
     * Attempts to connect to MySQL database.
     */
    private void connectToMySQL(Runnable onSuccess, Runnable onFallback) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                this.mysqlConnector = new MySQLConnector(pluginSettings);
                this.mySqlEnabled = true;

                if (pluginSettings.isBatchWriteEnabled()) {
                    this.batchWriteOptimizer = new BatchWriteOptimizer(
                        plugin, mysqlConnector, pluginSettings
                    );
                }

                this.priceDatabaseAPI = new MySQLPriceDatabaseAPIImpl(
                    mysqlConnector, plugin.getLogger()
                );

                // Non-blocking: chain initialization as async continuation
                // to avoid blocking the global region thread during boot (fixes C7).
                this.priceDatabaseAPI.initialize().thenAccept(v -> {
                    try {
                        initMySqlData();
                        tradeLogger.config("MySQL connection established");
                        runSync(onSuccess);
                    } catch (Exception e) {
                        tradeLogger.warning("MySQL init failed: " + e.getMessage());
                        this.mySqlEnabled = false;
                        initializeFileStorage();
                        runSync(onFallback);
                    }
                }).exceptionally(ex -> {
                    tradeLogger.warning("Price DB init failed: " + ex.getMessage());
                    this.mySqlEnabled = false;
                    initializeFileStorage();
                    runSync(onFallback);
                    return null;
                });

            } catch (Exception e) {
                tradeLogger.warning("MySQL connection failed, falling back to file storage: " + e.getMessage());
                this.mySqlEnabled = false;
                initializeFileStorage();

                // Run fallback callback on main thread
                runSync(onFallback);
            }
        });
    }

    /**
     * Initializes all MySQL-backed data classes and repositories.
     * Called after the price database API has finished initializing.
     */
    private void initMySqlData() throws Exception {
        this.playerData = new com.github.lye.database.PlayerData(plugin, mysqlConnector);
        this.playerData.createTable();

        this.globalStockData = new GlobalStockData(plugin, mysqlConnector);
        this.globalStockData.createTable();

        this.serverStateData = new ServerStateData(plugin, mysqlConnector);
        this.serverStateData.createTable();

        this.playerCollectionData = new PlayerCollectionData(plugin, mysqlConnector);
        this.playerCollectionData.createTable();

        this.serverCollectionData = new ServerCollectionData(plugin, mysqlConnector);
        this.serverCollectionData.createTable();

        this.mySQLShopRepository = new MySQLShopRepository(
            plugin, mysqlConnector, tradeLogger, batchWriteOptimizer
        );
        this.mySQLShopRepository.initSchema();
    }

    /**
     * Initializes file-based storage as fallback.
     */
    private void initializeFileStorage() {
        this.playerCollectionData = new FilePlayerCollectionData(plugin);
        this.serverCollectionData = new FileServerCollectionData(plugin);
        this.priceDatabaseAPI = new PlaceholderPriceDatabaseAPI();
    }

    /**
     * Runs a runnable synchronously on the global region.
     */
    private void runSync(Runnable runnable) {
        if (runnable != null) {
            com.github.lye.util.FoliaSchedulers.runGlobal(plugin, runnable);
        }
    }

    /**
     * Shuts down database connections.
     * <p>
     * Order matters: the batch write optimizer must flush all remaining queued
     * writes to the database <b>before</b> the underlying connection pool is closed,
     * otherwise queued writes are silently discarded.
     */
    public void shutdown() {
        // 1. Flush remaining batch writes while the connection is still open
        if (batchWriteOptimizer != null) {
            try {
                batchWriteOptimizer.shutdown();
            } catch (Exception e) {
                tradeLogger.warning(
                        "BatchWriteOptimizer shutdown failed: " + e.getMessage());
            }
        }

        // 2. Close the connection pool
        if (mysqlConnector != null) {
            try {
                mysqlConnector.close();
                tradeLogger.config("MySQL connection closed");
            } catch (Exception e) {
                exceptionHandler.handleDatabaseException("CLOSE",
                    e instanceof SQLException ? (SQLException) e : new SQLException(e));
            }
        }
    }

    // ==================== GETTERS ====================

    public MySQLConnector getMysqlConnector() {
        return mysqlConnector;
    }

    public BatchWriteOptimizer getBatchWriteOptimizer() {
        return batchWriteOptimizer;
    }

    public boolean isMySqlEnabled() {
        return mySqlEnabled;
    }

    public MySQLShopRepository getMySQLShopRepository() {
        return mySQLShopRepository;
    }

    public com.github.lye.database.PlayerData getPlayerData() {
        return playerData;
    }

    public GlobalStockData getGlobalStockData() {
        return globalStockData;
    }

    public ServerStateData getServerStateData() {
        return serverStateData;
    }

    public IPlayerCollectionData getPlayerCollectionData() {
        return playerCollectionData;
    }

    public IServerCollectionData getServerCollectionData() {
        return serverCollectionData;
    }

    public PriceDatabaseAPI getPriceDatabaseAPI() {
        return priceDatabaseAPI;
    }

    /**
     * Placeholder price database API for when MySQL is not available.
     */
    private static class PlaceholderPriceDatabaseAPI implements PriceDatabaseAPI {
        @Override
        public java.util.concurrent.CompletableFuture<java.util.Optional<com.github.lye.pricing.model.PricingData>> getPricingData(com.github.lye.pricing.model.ItemId id) {
            return java.util.concurrent.CompletableFuture.completedFuture(java.util.Optional.empty());
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> savePricingData(com.github.lye.pricing.model.PricingData d) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Boolean> itemExists(com.github.lye.pricing.model.ItemId id) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> initialize() {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> shutdown() {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public Double getOrNull(String k) {
            return null;
        }

        @Override
        public void upsert(String k, double p) {
        }
    }
}
