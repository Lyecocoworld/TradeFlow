package com.yourplugin.pricing.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoubleUnaryOperator;
import java.util.function.ToDoubleFunction;

public class PricingParams {
    private final double defaultMargin;
    private final double defaultTax;
    private final double defaultVolatility;
    private final double machineTimeCostPerSecond;
    private final double byproductRatio;
    private final Map<String, Double> fuelCosts;
    private final java.util.function.ToDoubleFunction<ItemId> toolWearCostFn; // Function to calculate tool wear cost

    public PricingParams(double defaultMargin, double defaultTax, double defaultVolatility, double machineTimeCostPerSecond, double byproductRatio, Map<String, Double> fuelCosts, java.util.function.ToDoubleFunction<ItemId> toolWearCostFn) {
        this.defaultMargin = defaultMargin;
        this.defaultTax = defaultTax;
        this.defaultVolatility = defaultVolatility;
        this.machineTimeCostPerSecond = machineTimeCostPerSecond;
        this.byproductRatio = byproductRatio;
        this.fuelCosts = Collections.unmodifiableMap(Objects.requireNonNull(fuelCosts, "Fuel costs map cannot be null"));
        this.toolWearCostFn = Objects.requireNonNull(toolWearCostFn, "Tool wear cost function cannot be null");
    }

    public double getDefaultMargin() {
        return defaultMargin;
    }

    public double getDefaultTax() {
        return defaultTax;
    }

    public double getDefaultVolatility() {
        return defaultVolatility;
    }

    public double getMachineTimeCostPerSecond() {
        return machineTimeCostPerSecond;
    }

    public double getByproductRatio() {
        return byproductRatio;
    }

    public Map<String, Double> getFuelCosts() {
        return fuelCosts;
    }

    public java.util.function.ToDoubleFunction<ItemId> getToolWearCostFn() {
        return toolWearCostFn;
    }

    // Builder pattern or static factory methods could be added for easier construction
}
