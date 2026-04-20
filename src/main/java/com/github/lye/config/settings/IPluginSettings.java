package com.github.lye.config.settings;

import com.github.lye.data.CollectFirst;
import java.util.List;

public interface IPluginSettings {
    // --- Database ---
    boolean isDatabaseEnabled();
    String getDatabaseType();
    String getDatabaseHost();
    int getDatabasePort();
    String getDatabaseName();
    String getDatabaseUser();
    String getDatabasePassword();
    boolean isDatabaseSslEnabled();
    boolean isDatabasePublicKeyRetrieval();
    int getDatabasePoolSize();
    int getDatabaseMinIdle();
    int getMaxTransactionHistory();
    
    // Replication
    boolean isDatabaseReplicationEnabled();
    List<String> getDatabaseReadReplicas();
    String getDatabaseLoadBalancing();

    // Circuit Breaker
    boolean isDatabaseCircuitBreakerEnabled();
    int getDatabaseFailureThreshold();
    int getDatabaseCircuitTimeout();

    // --- Redis ---
    boolean isRedisEnabled();
    String getRedisMode();
    String getRedisServerId();
    String getRedisHost();
    int getRedisPort();
    String getRedisUsername();
    String getRedisPassword();
    int getRedisDatabase();
    
    // Connection Tuning
    int getRedisTimeout();
    int getRedisRetryAttempts();
    int getRedisRetryInterval();
    int getRedisPoolSize();

    // Channels
    String getRedisChannelGlobal();
    String getRedisChannelPrices();
    String getRedisChannelHeartbeat();

    // --- Batch Write ---
    boolean isBatchWriteEnabled();
    int getBatchWriteSize();
    int getBatchWriteFlushInterval();
    boolean isBatchWriteAsync();

    // --- Cache ---
    boolean isQueryCacheEnabled();
    int getQueryCacheLocalSize();
    int getQueryCacheTtl();

    // --- Monitoring ---
    boolean isMonitoringEnabled();
    boolean isPrometheusEnabled();
    int getPrometheusPort();

    // --- System ---
    String getServerName();
    String getLogLevel();
    String getLocale();
    boolean isOnlineMode();
    boolean isEnableSellLimits();
    
    // --- Legacy / Mixed ---
    boolean isDurabilityFunction();
    double getTimePeriod();
    double getVolatility();
    double getSellPriceDifference();
    Integer getMinimumPlayers();
    double getTutorialUpdate();
    boolean isWebServer();
    Integer getPort();
    String getBindAddress();
    boolean isEnableCollection();
    boolean isEnableLoans();
    int getMaxActiveLoans();
    double getInterest();
    CollectFirst.CollectFirstSetting getCollectFirstDefault();
    
    // Dynamic Economy
    boolean isEnableDynamicPricing();
    boolean isBankIndefinite();
    String getCentralBankAccount();
    double getInitialCapital();
    int getTargetPopulation();

    // --- Reputation ---
    double getReputationDefault();
    double getReputationInsiderThreshold();
    double getReputationTierPenaltyHigh();
    double getReputationTierPenaltyLow();
    double getReputationTierInsiderBonus();
    double getReputationTierVeteranBonus();
    double getReputationSellScarcityBonus();
    double getReputationSellSurplusPenalty();
    double getReputationBuyScarcityPenalty();
    double getReputationBuySurplusBonus();
    double getReputationSellTickBonus();
    double getReputationBuyTickBonus();
    double getReputationScarcityThreshold();
    double getReputationSurplusPriceRatio();
    double getReputationSurplusStockRatio();

    // --- Loans ---
    double getLoanInterestMultiplier();

    // --- GMQ ---
    String getGmqRestockTime();
}