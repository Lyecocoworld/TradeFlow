package com.github.lye.config.settings;

public interface IPricingSettings {
    boolean isAllowUncraftEdges();
    boolean isAllowCompressionEdges();
    boolean isTreatReversibleAsDerived();
    double getAntiArbitrageFee();
    double getStartPrice();
    double getVolatility();

    // --- Central Bank ---
    double getPublicOrderBonus();
    double getPublicOrderThreshold();
    double getExpansionThreshold();
    double getAusterityThreshold();
    double getActivityAlpha();
    int getDefaultDailyQuota();
    int getDefaultPopulation();
    int getDefaultInitialStock();
    double getBootstrapThreshold();
    double getSaturationMultiplier();

    // --- Dynamic Spread ---
    double getDynamicSpreadActivityDivisor();
    double getDynamicSpreadMaxBase();
    double getDynamicSpreadMaxFinal();

    // --- Shop Pricing ---
    double getPriceStrengthM();
    double getPriceStrengthZ();

    // --- Events ---
    long getEventMinIntervalMs();
    long getEventMaxIntervalMs();
}
