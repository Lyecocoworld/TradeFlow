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
    double getExpansionThreshold();
    double getAusterityThreshold();
    double getActivityAlpha();
    int getDefaultDailyQuota();
    int getDefaultPopulation();
    int getDefaultInitialStock();

    // --- Shop Pricing ---
    double getPriceStrengthM();
    double getPriceStrengthZ();

    // --- Events ---
    long getEventMinIntervalMs();
    long getEventMaxIntervalMs();
}
