package com.github.lye.config.settings.impl;

import com.github.lye.config.settings.IPricingSettings;
import com.github.lye.util.Format;
import com.github.lye.util.TradeFlowLogger;
import org.bukkit.configuration.file.YamlConfiguration;

public class DefaultPricingSettings implements IPricingSettings {

    private final boolean allowUncraftEdges;
    private final boolean allowCompressionEdges;
    private final boolean treatReversibleAsDerived;
    private final double antiArbitrageFee;
    private final double startPrice;
    private final double volatility;
    private final TradeFlowLogger logger;

    // Central Bank
    private final double publicOrderBonus;
    private final double expansionThreshold;
    private final double austerityThreshold;
    private final double activityAlpha;
    private final int defaultDailyQuota;
    private final int defaultPopulation;
    private final int defaultInitialStock;

    // Shop Pricing
    private final double priceStrengthM;
    private final double priceStrengthZ;

    // Events
    private final long eventMinIntervalMs;
    private final long eventMaxIntervalMs;

    public DefaultPricingSettings(YamlConfiguration configYml, TradeFlowLogger logger) {
        this.logger = logger;

        this.allowUncraftEdges = configYml.getBoolean("pricing.allow-uncraft-edges", false);
        logger.finer("Allow Uncraft Edges: " + allowUncraftEdges);
        this.allowCompressionEdges = configYml.getBoolean("pricing.allow-compression-edges", true);
        logger.finer("Allow Compression Edges: " + allowCompressionEdges);
        this.treatReversibleAsDerived = configYml.getBoolean("pricing.treat-reversible-as-derived", true);
        logger.finer("Treat Reversible As Derived: " + treatReversibleAsDerived);
        this.antiArbitrageFee = configYml.getDouble("pricing.anti-arbitrage-fee", 0.01);
        logger.finer("Anti Arbitrage Fee: " + antiArbitrageFee);
        this.startPrice = configYml.getDouble("start-price", 10.0);
        logger.finer("Default Start Price: " + startPrice);
        this.volatility = configYml.getDouble("pricing.default-volatility", 0.5);
        logger.finer("Default Volatility: " + volatility);

        // Central Bank
        this.publicOrderBonus = configYml.getDouble("central-bank.public-order-bonus", 0.20);
        logger.finer("Central Bank Public Order Bonus: " + publicOrderBonus);
        this.expansionThreshold = configYml.getDouble("central-bank.expansion-threshold", 1.5);
        logger.finer("Central Bank Expansion Threshold: " + expansionThreshold);
        this.austerityThreshold = configYml.getDouble("central-bank.austerity-threshold", 0.5);
        logger.finer("Central Bank Austerity Threshold: " + austerityThreshold);
        this.activityAlpha = configYml.getDouble("central-bank.activity-alpha", 0.1);
        logger.finer("Central Bank Activity Alpha: " + activityAlpha);
        this.defaultDailyQuota = configYml.getInt("central-bank.default-daily-quota", 64);
        logger.finer("Central Bank Default Daily Quota: " + defaultDailyQuota);
        this.defaultPopulation = configYml.getInt("central-bank.default-population", 100);
        logger.finer("Central Bank Default Population: " + defaultPopulation);
        this.defaultInitialStock = configYml.getInt("central-bank.default-initial-stock", 1280);
        logger.finer("Central Bank Default Initial Stock: " + defaultInitialStock);

        // Shop Pricing
        this.priceStrengthM = configYml.getDouble("pricing.price-strength-m", 0.05);
        logger.finer("Price Strength M: " + priceStrengthM);
        this.priceStrengthZ = configYml.getDouble("pricing.price-strength-z", 1.75);
        logger.finer("Price Strength Z: " + priceStrengthZ);

        // Events
        this.eventMinIntervalMs = configYml.getLong("events.min-interval-ms", 3600000L);
        logger.finer("Event Min Interval Ms: " + eventMinIntervalMs);
        this.eventMaxIntervalMs = configYml.getLong("events.max-interval-ms", 7200000L);
        logger.finer("Event Max Interval Ms: " + eventMaxIntervalMs);
    }

    @Override
    public boolean isAllowUncraftEdges() {
        return allowUncraftEdges;
    }

    @Override
    public boolean isAllowCompressionEdges() {
        return allowCompressionEdges;
    }

    @Override
    public boolean isTreatReversibleAsDerived() {
        return treatReversibleAsDerived;
    }

    @Override
    public double getAntiArbitrageFee() {
        return antiArbitrageFee;
    }

    @Override
    public double getStartPrice() {
        return startPrice;
    }

    @Override
    public double getVolatility() {
        return volatility;
    }

    @Override
    public double getPublicOrderBonus() {
        return publicOrderBonus;
    }

    @Override
    public double getExpansionThreshold() {
        return expansionThreshold;
    }

    @Override
    public double getAusterityThreshold() {
        return austerityThreshold;
    }

    @Override
    public double getActivityAlpha() {
        return activityAlpha;
    }

    @Override
    public int getDefaultDailyQuota() {
        return defaultDailyQuota;
    }

    @Override
    public int getDefaultPopulation() {
        return defaultPopulation;
    }

    @Override
    public int getDefaultInitialStock() {
        return defaultInitialStock;
    }

    @Override
    public double getPriceStrengthM() {
        return priceStrengthM;
    }

    @Override
    public double getPriceStrengthZ() {
        return priceStrengthZ;
    }

    @Override
    public long getEventMinIntervalMs() {
        return eventMinIntervalMs;
    }

    @Override
    public long getEventMaxIntervalMs() {
        return eventMaxIntervalMs;
    }
}
