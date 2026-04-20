package com.github.lye.config.settings.impl;

import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.data.CollectFirst;
import com.github.lye.util.Format;
import com.github.lye.util.TradeFlowLogger;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

public class DefaultPluginSettings implements IPluginSettings {

    private static YamlConfiguration safeConfig(YamlConfiguration config) {
        return config != null ? config : new YamlConfiguration();
    }

    private final TradeFlowLogger logger;

    // --- DATABASE ---
    private final boolean databaseEnabled;
    private final String databaseType;
    private final String databaseHost;
    private final int databasePort;
    private final String databaseName;
    private final String databaseUser;
    private final String databasePassword;
    private final boolean databaseSslEnabled;
    private final boolean databasePublicKeyRetrieval;
    private final int databasePoolSize;
    private final int databaseMinIdle;
    private final int maxTransactionHistory;
    private final boolean databaseReplicationEnabled;
    private final List<String> databaseReadReplicas;
    private final String databaseLoadBalancing;
    private final boolean databaseCircuitBreakerEnabled;
    private final int databaseFailureThreshold;
    private final int databaseCircuitTimeout;

    // --- REDIS ---
    private final boolean redisEnabled;
    private final String redisMode;
    private final String redisServerId;
    private final String redisHost;
    private final int redisPort;
    private final String redisUsername;
    private final String redisPassword;
    private final int redisDatabase;
    private final int redisTimeout;
    private final int redisRetryAttempts;
    private final int redisRetryInterval;
    private final int redisPoolSize;
    private final String redisChannelGlobal;
    private final String redisChannelPrices;
    private final String redisChannelHeartbeat;

    // --- BATCH WRITE ---
    private final boolean batchWriteEnabled;
    private final int batchWriteSize;
    private final int batchWriteFlushInterval;
    private final boolean batchWriteAsync;

    // --- CACHE ---
    private final boolean queryCacheEnabled;
    private final int queryCacheLocalSize;
    private final int queryCacheTtl;

    // --- MONITORING ---
    private final boolean monitoringEnabled;
    private final boolean prometheusEnabled;
    private final int prometheusPort;

    // --- SYSTEM ---
    private final String serverName;
    private final String logLevel;
    private final String locale;
    private final boolean onlineMode;
    private final boolean enableSellLimits;

    // --- PRICING ---
    private final boolean enableDynamicPricing;
    private final boolean bankIndefinite;
    private final String centralBankAccount;
    private final double initialCapital;
    private final int targetPopulation;
    private final double timePeriod;
    private final double volatility;
    private final double sellPriceDifference;
    private final boolean durabilityFunction;
    private final Integer minimumPlayers;

    // --- FEATURES ---
    private final boolean enableLoans;
    private final int maxActiveLoans;
    private final double interest;
    private final double loanInterestMultiplier;
    private final boolean enableCollection;
    private final CollectFirst.CollectFirstSetting collectFirstDefault;
    private final double tutorialUpdate;

    private final boolean webServer;
    private final Integer port;
    private final String bindAddress;

    // --- REPUTATION ---
    private final double reputationDefault;
    private final double reputationInsiderThreshold;
    private final double reputationTierPenaltyHigh;
    private final double reputationTierPenaltyLow;
    private final double reputationTierInsiderBonus;
    private final double reputationTierVeteranBonus;
    private final double reputationSellScarcityBonus;
    private final double reputationSellSurplusPenalty;
    private final double reputationBuyScarcityPenalty;
    private final double reputationBuySurplusBonus;
    private final double reputationSellTickBonus;
    private final double reputationBuyTickBonus;
    private final double reputationScarcityThreshold;
    private final double reputationSurplusPriceRatio;
    private final double reputationSurplusStockRatio;

    // --- GMQ ---
    private final String gmqRestockTime;

    public DefaultPluginSettings(YamlConfiguration general, YamlConfiguration pricing, YamlConfiguration features, TradeFlowLogger logger) {
        this.logger = logger;

        general = safeConfig(general);
        pricing = safeConfig(pricing);
        features = safeConfig(features);

        // --- GENERAL MODULE parsing ---
        this.databaseEnabled = general.getBoolean("database.enabled", false);
        this.databaseType = general.getString("database.type", "mysql");
        this.databaseHost = general.getString("database.host", "127.0.0.1");
        this.databasePort = general.getInt("database.port", 3306);
        this.databaseName = general.getString("database.name", "tradeflow");
        this.databaseUser = general.getString("database.user", "CHANGEME_db_user");
        this.databasePassword = general.getString("database.password", "CHANGEME_db_password");
        this.databaseSslEnabled = general.getBoolean("database.ssl.enabled", false);
        this.databasePublicKeyRetrieval = general.getBoolean("database.allow-public-key-retrieval", true);
        this.databasePoolSize = general.getInt("database.pool.size", 50);
        this.databaseMinIdle = general.getInt("database.pool.min-idle", 10);
        this.maxTransactionHistory = general.getInt("database.max-transaction-history", 1000);
        
        this.databaseReplicationEnabled = general.getBoolean("database.replication.enabled", false);
        this.databaseReadReplicas = general.getStringList("database.replication.read-replicas");
        this.databaseLoadBalancing = general.getString("database.replication.load-balancing", "least_connections");
        
        this.databaseCircuitBreakerEnabled = general.getBoolean("database.circuit-breaker.enabled", true);
        this.databaseFailureThreshold = general.getInt("database.circuit-breaker.failure-threshold", 5);
        this.databaseCircuitTimeout = general.getInt("database.circuit-breaker.timeout", 60000);

        this.redisEnabled = general.getBoolean("redis.enabled", false);
        this.redisMode = general.getString("redis.mode", "single");
        this.redisServerId = general.getString("redis.server-id", "tf-1");
        this.redisHost = general.getString("redis.single.host", "127.0.0.1");
        this.redisPort = general.getInt("redis.single.port", 6379);
        this.redisUsername = general.getString("redis.auth.username", "");
        this.redisPassword = general.getString("redis.auth.password", "");
        this.redisDatabase = general.getInt("redis.auth.database", 0);
        this.redisTimeout = general.getInt("redis.connection.timeout", 3000);
        this.redisRetryAttempts = general.getInt("redis.connection.retry-attempts", 3);
        this.redisRetryInterval = general.getInt("redis.connection.retry-interval", 1500);
        this.redisPoolSize = general.getInt("redis.connection.connection-pool-size", 64);
        
        this.redisChannelGlobal = general.getString("redis.channels.global", "tf:global");
        this.redisChannelPrices = general.getString("redis.channels.prices", "tf:prices");
        this.redisChannelHeartbeat = general.getString("redis.channels.heartbeat", "tf:heartbeat");

        this.batchWriteEnabled = general.getBoolean("batch-write.enabled", true);
        this.batchWriteSize = general.getInt("batch-write.batch-size", 100);
        this.batchWriteFlushInterval = general.getInt("batch-write.flush-interval", 2000);
        this.batchWriteAsync = general.getBoolean("batch-write.async-execution", true);

        this.queryCacheEnabled = general.getBoolean("cache.query-cache.enabled", true);
        this.queryCacheLocalSize = general.getInt("cache.query-cache.local-size", 5000);
        this.queryCacheTtl = general.getInt("cache.query-cache.ttl-seconds", 120);

        this.monitoringEnabled = general.getBoolean("monitoring.enabled", true);
        this.prometheusEnabled = general.getBoolean("monitoring.prometheus.enabled", true);
        this.prometheusPort = general.getInt("monitoring.prometheus.port", 9090);

        this.serverName = general.getString("system.server-name", "server-1");
        this.logLevel = general.getString("system.log-level", "INFO");
        this.locale = general.getString("system.locale", "en_US");
        this.onlineMode = general.getBoolean("system.online-mode", true);
        this.enableSellLimits = general.getBoolean("system.enable-sell-limits", false);

        this.webServer = general.getBoolean("web-server.enabled", true);
        this.port = general.getInt("web-server.port", 8989);
        this.bindAddress = general.getString("web-server.bind-address", "127.0.0.1");

        // --- PRICING MODULE parsing ---
        this.enableDynamicPricing = pricing.getBoolean("dynamic-pricing.enabled", true);
        this.bankIndefinite = pricing.getBoolean("dynamic-pricing.bank-indefinite", true);
        this.centralBankAccount = pricing.getString("dynamic-pricing.central-bank-account", "ServerBank");
        this.initialCapital = pricing.getDouble("dynamic-pricing.initial-capital", -1.0);
        this.targetPopulation = pricing.getInt("dynamic-pricing.target-population", 1000);

        this.minimumPlayers = pricing.getInt("market.minimum-players", 2);
        this.timePeriod = pricing.getDouble("market.time-period", 30);
        this.volatility = pricing.getDouble("market.volatility", 1.5);
        this.sellPriceDifference = pricing.getDouble("market.sell-price-difference", 40);
        this.durabilityFunction = pricing.getBoolean("market.durability-function", true);

        // --- FEATURES MODULE parsing ---
        this.enableLoans = features.getBoolean("loans.enabled", false);
        this.maxActiveLoans = features.getInt("loans.max-active-loans", 5);
        this.interest = features.getDouble("loans.interest-rate", 0.05);
        this.loanInterestMultiplier = features.getDouble("loans.interest-multiplier", 0.01);

        this.enableCollection = features.getBoolean("collection.enabled", true);
        String cfMode = features.getString("collection.default-mode", "PLAYER");
        this.collectFirstDefault = CollectFirst.CollectFirstSetting.valueOf(cfMode.toUpperCase());
        this.tutorialUpdate = features.getDouble("tutorial.update-interval", 300);

        // --- REPUTATION MODULE parsing ---
        this.reputationDefault = features.getDouble("reputation.default", 50.0);
        this.reputationInsiderThreshold = features.getDouble("reputation.insider-threshold", 70.0);
        this.reputationTierPenaltyHigh = features.getDouble("reputation.tier-modifiers.penalty-high", 0.05);
        this.reputationTierPenaltyLow = features.getDouble("reputation.tier-modifiers.penalty-low", 0.02);
        this.reputationTierInsiderBonus = features.getDouble("reputation.tier-modifiers.insider-bonus", 0.03);
        this.reputationTierVeteranBonus = features.getDouble("reputation.tier-modifiers.veteran-bonus", 0.05);
        this.reputationSellScarcityBonus = features.getDouble("reputation.trade-deltas.sell-scarcity-bonus", 2.0);
        this.reputationSellSurplusPenalty = features.getDouble("reputation.trade-deltas.sell-surplus-penalty", -1.0);
        this.reputationBuyScarcityPenalty = features.getDouble("reputation.trade-deltas.buy-scarcity-penalty", -2.0);
        this.reputationBuySurplusBonus = features.getDouble("reputation.trade-deltas.buy-surplus-bonus", 1.0);
        this.reputationSellTickBonus = features.getDouble("reputation.trade-deltas.sell-tick-bonus", 0.1);
        this.reputationBuyTickBonus = features.getDouble("reputation.trade-deltas.buy-tick-bonus", 0.05);
        this.reputationScarcityThreshold = features.getDouble("reputation.thresholds.scarcity-ratio", 0.25);
        this.reputationSurplusPriceRatio = features.getDouble("reputation.thresholds.surplus-price-ratio", 2.0);
        this.reputationSurplusStockRatio = features.getDouble("reputation.thresholds.surplus-stock-ratio", 1.5);

        // --- GMQ MODULE parsing (from general.yml) ---
        this.gmqRestockTime = general.getString("gmq.restock-time", "SUNDAY 18:00");

        // Setup Logger & Locale
        Format.getLog().setLevel(Level.parse(logLevel));
        Format.loadLocale(this.locale);

        warnDefaultCredentials();
    }

    private void warnDefaultCredentials() {
        if (!databaseEnabled) { return; }
        if (databaseUser.startsWith("CHANGEME_") || databasePassword.startsWith("CHANGEME_")) {
            logger.severe("╔══════════════════════════════════════════════════════════════╗");
            logger.severe("║  SECURITY WARNING: Default database credentials detected!   ║");
            logger.severe("║  Change database.user and database.password in config.yml   ║");
            logger.severe("║  Using default credentials is a critical security risk.     ║");
            logger.severe("╚══════════════════════════════════════════════════════════════╝");
        }
    }

    // --- GETTERS (Database) ---
    @Override public boolean isDatabaseEnabled() { return databaseEnabled; }
    @Override public String getDatabaseType() { return databaseType; }
    @Override public String getDatabaseHost() { return databaseHost; }
    @Override public int getDatabasePort() { return databasePort; }
    @Override public String getDatabaseName() { return databaseName; }
    @Override public String getDatabaseUser() { return databaseUser; }
    @Override public String getDatabasePassword() { return databasePassword; }
    @Override public boolean isDatabaseSslEnabled() { return databaseSslEnabled; }
    @Override public boolean isDatabasePublicKeyRetrieval() { return databasePublicKeyRetrieval; }
    @Override public int getDatabasePoolSize() { return databasePoolSize; }
    @Override public int getDatabaseMinIdle() { return databaseMinIdle; }
    @Override public int getMaxTransactionHistory() { return maxTransactionHistory; }
    @Override public boolean isDatabaseReplicationEnabled() { return databaseReplicationEnabled; }
    @Override public List<String> getDatabaseReadReplicas() { return databaseReadReplicas; }
    @Override public String getDatabaseLoadBalancing() { return databaseLoadBalancing; }
    @Override public boolean isDatabaseCircuitBreakerEnabled() { return databaseCircuitBreakerEnabled; }
    @Override public int getDatabaseFailureThreshold() { return databaseFailureThreshold; }
    @Override public int getDatabaseCircuitTimeout() { return databaseCircuitTimeout; }

    // --- GETTERS (Redis) ---
    @Override public boolean isRedisEnabled() { return redisEnabled; }
    @Override public String getRedisMode() { return redisMode; }
    @Override public String getRedisServerId() { return redisServerId; }
    @Override public String getRedisHost() { return redisHost; }
    @Override public int getRedisPort() { return redisPort; }
    @Override public String getRedisUsername() { return redisUsername; }
    @Override public String getRedisPassword() { return redisPassword; }
    @Override public int getRedisDatabase() { return redisDatabase; }
    @Override public int getRedisTimeout() { return redisTimeout; }
    @Override public int getRedisRetryAttempts() { return redisRetryAttempts; }
    @Override public int getRedisRetryInterval() { return redisRetryInterval; }
    @Override public int getRedisPoolSize() { return redisPoolSize; }
    @Override public String getRedisChannelGlobal() { return redisChannelGlobal; }
    @Override public String getRedisChannelPrices() { return redisChannelPrices; }
    @Override public String getRedisChannelHeartbeat() { return redisChannelHeartbeat; }

    // --- GETTERS (Batch Write) ---
    @Override public boolean isBatchWriteEnabled() { return batchWriteEnabled; }
    @Override public int getBatchWriteSize() { return batchWriteSize; }
    @Override public int getBatchWriteFlushInterval() { return batchWriteFlushInterval; }
    @Override public boolean isBatchWriteAsync() { return batchWriteAsync; }

    // --- GETTERS (Cache) ---
    @Override public boolean isQueryCacheEnabled() { return queryCacheEnabled; }
    @Override public int getQueryCacheLocalSize() { return queryCacheLocalSize; }
    @Override public int getQueryCacheTtl() { return queryCacheTtl; }

    // --- GETTERS (Monitoring) ---
    @Override public boolean isMonitoringEnabled() { return monitoringEnabled; }
    @Override public boolean isPrometheusEnabled() { return prometheusEnabled; }
    @Override public int getPrometheusPort() { return prometheusPort; }

    // --- GETTERS (System) ---
    @Override public String getServerName() { return serverName; }
    @Override public String getLogLevel() { return logLevel; }
    @Override public String getLocale() { return locale; }
    @Override public boolean isOnlineMode() { return onlineMode; }
    @Override public boolean isEnableSellLimits() { return enableSellLimits; }

    // --- GETTERS (Mixed/Legacy) ---
    @Override public boolean isDurabilityFunction() { return durabilityFunction; }
    @Override public double getTimePeriod() { return timePeriod; }
    @Override public double getVolatility() { return volatility; }
    @Override public double getSellPriceDifference() { return sellPriceDifference; }
    @Override public Integer getMinimumPlayers() { return minimumPlayers; }
    @Override public double getTutorialUpdate() { return tutorialUpdate; }
    @Override public boolean isWebServer() { return webServer; }
    @Override public Integer getPort() { return port; }
    @Override public String getBindAddress() { return bindAddress; }
    @Override public boolean isEnableCollection() { return enableCollection; }
    @Override public boolean isEnableLoans() { return enableLoans; }
    @Override public int getMaxActiveLoans() { return maxActiveLoans; }
    @Override public double getInterest() { return interest; }
    @Override public CollectFirst.CollectFirstSetting getCollectFirstDefault() { return collectFirstDefault; }
    // Dynamic Economy
    @Override public boolean isEnableDynamicPricing() { return enableDynamicPricing; }
    @Override public boolean isBankIndefinite() { return bankIndefinite; }
    @Override public String getCentralBankAccount() { return centralBankAccount; }
    @Override public double getInitialCapital() { return initialCapital; }
    @Override public int getTargetPopulation() { return targetPopulation; }

    // --- GETTERS (Reputation) ---
    @Override public double getReputationDefault() { return reputationDefault; }
    @Override public double getReputationInsiderThreshold() { return reputationInsiderThreshold; }
    @Override public double getReputationTierPenaltyHigh() { return reputationTierPenaltyHigh; }
    @Override public double getReputationTierPenaltyLow() { return reputationTierPenaltyLow; }
    @Override public double getReputationTierInsiderBonus() { return reputationTierInsiderBonus; }
    @Override public double getReputationTierVeteranBonus() { return reputationTierVeteranBonus; }
    @Override public double getReputationSellScarcityBonus() { return reputationSellScarcityBonus; }
    @Override public double getReputationSellSurplusPenalty() { return reputationSellSurplusPenalty; }
    @Override public double getReputationBuyScarcityPenalty() { return reputationBuyScarcityPenalty; }
    @Override public double getReputationBuySurplusBonus() { return reputationBuySurplusBonus; }
    @Override public double getReputationSellTickBonus() { return reputationSellTickBonus; }
    @Override public double getReputationBuyTickBonus() { return reputationBuyTickBonus; }
    @Override public double getReputationScarcityThreshold() { return reputationScarcityThreshold; }
    @Override public double getReputationSurplusPriceRatio() { return reputationSurplusPriceRatio; }
    @Override public double getReputationSurplusStockRatio() { return reputationSurplusStockRatio; }

    // --- GETTERS (Loans) ---
    @Override public double getLoanInterestMultiplier() { return loanInterestMultiplier; }

    // --- GETTERS (GMQ) ---
    @Override public String getGmqRestockTime() { return gmqRestockTime; }
}
