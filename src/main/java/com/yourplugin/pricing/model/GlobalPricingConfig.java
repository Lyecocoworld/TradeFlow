package com.yourplugin.pricing.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GlobalPricingConfig(
    String mode,
    @JsonProperty("m") double margin,
    @JsonProperty("t") double tax,
    @JsonProperty("machine_time_cost_per_second") double machineTimeCostPerSecond,
    @JsonProperty("byproduct_ratio") double byproductRatio
) {
    public GlobalPricingConfig() {
        this("auto", 0.10, 0.05, 0.02, 0.7);
    }
}
