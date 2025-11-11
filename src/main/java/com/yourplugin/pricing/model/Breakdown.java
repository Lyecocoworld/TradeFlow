package com.yourplugin.pricing.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public class Breakdown {
    public enum SourceType {
        SHOP, AUTO
    }

    private final ItemId itemId;
    private final SourceType sourceType;
    private final double calculatedPrice;
    private final Map<ItemId, Double> inputs;
    private final double energyCost;
    private final double margin;
    private final double tax;
    private final double minPrice;
    private final double maxPrice;
    private final String stableHash;

    public Breakdown(ItemId itemId, SourceType sourceType, double calculatedPrice, Map<ItemId, Double> inputs, double energyCost, double margin, double tax, double minPrice, double maxPrice, String stableHash) {
        this.itemId = Objects.requireNonNull(itemId, "Item ID cannot be null");
        this.sourceType = Objects.requireNonNull(sourceType, "Source type cannot be null");
        this.calculatedPrice = calculatedPrice;
        this.inputs = Collections.unmodifiableMap(Objects.requireNonNull(inputs, "Inputs map cannot be null"));
        this.energyCost = energyCost;
        this.margin = margin;
        this.tax = tax;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.stableHash = Objects.requireNonNull(stableHash, "Stable hash cannot be null");
    }

    public ItemId getItemId() {
        return itemId;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public double getCalculatedPrice() {
        return calculatedPrice;
    }

    public Map<ItemId, Double> getInputs() {
        return inputs;
    }

    public double getEnergyCost() {
        return energyCost;
    }

    public double getMargin() {
        return margin;
    }

    public double getTax() {
        return tax;
    }

    public double getMinPrice() {
        return minPrice;
    }

    public double getMaxPrice() {
        return maxPrice;
    }

    public String getStableHash() {
        return stableHash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Breakdown breakdown = (Breakdown) o;
        return Double.compare(breakdown.calculatedPrice, calculatedPrice) == 0 && Double.compare(breakdown.energyCost, energyCost) == 0 && Double.compare(breakdown.margin, margin) == 0 && Double.compare(breakdown.tax, tax) == 0 && Double.compare(breakdown.minPrice, minPrice) == 0 && Double.compare(breakdown.maxPrice, maxPrice) == 0 && itemId.equals(breakdown.itemId) && sourceType == breakdown.sourceType && inputs.equals(breakdown.inputs) && stableHash.equals(breakdown.stableHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, sourceType, calculatedPrice, inputs, energyCost, margin, tax, minPrice, maxPrice, stableHash);
    }

    @Override
    public String toString() {
        return "Breakdown{" +
               "itemId=" + itemId +
               ", sourceType=" + sourceType +
               ", calculatedPrice=" + calculatedPrice +
               ", inputs=" + inputs +
               ", energyCost=" + energyCost +
               ", margin=" + margin +
               ", tax=" + tax +
               ", minPrice=" + minPrice +
               ", maxPrice=" + maxPrice +
               ", stableHash='" + stableHash + '\'' +
               '}';
    }
}
