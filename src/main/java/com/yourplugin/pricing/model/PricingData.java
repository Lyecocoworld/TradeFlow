package com.yourplugin.pricing.model;

import java.util.Objects;

public class PricingData {
    private final ItemId itemId;
    private final double price;
    private final double volatility;
    private final long lastUpdated;
    private final String dataHash; // To detect changes in source config

    public PricingData(ItemId itemId, double price, double volatility, long lastUpdated, String dataHash) {
        this.itemId = Objects.requireNonNull(itemId, "Item ID cannot be null");
        this.price = price;
        this.volatility = volatility;
        this.lastUpdated = lastUpdated;
        this.dataHash = Objects.requireNonNull(dataHash, "Data hash cannot be null");
    }

    public ItemId getItemId() {
        return itemId;
    }

    public double getPrice() {
        return price;
    }

    public double getVolatility() {
        return volatility;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public String getDataHash() {
        return dataHash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PricingData that = (PricingData) o;
        return Double.compare(that.price, price) == 0 &&
               Double.compare(that.volatility, volatility) == 0 &&
               lastUpdated == that.lastUpdated &&
               itemId.equals(that.itemId) &&
               dataHash.equals(that.dataHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, price, volatility, lastUpdated, dataHash);
    }

    @Override
    public String toString() {
        return "PricingData{" +
               "itemId=" + itemId +
               ", price=" + price +
               ", volatility=" + volatility +
               ", lastUpdated=" + lastUpdated +
               ", dataHash='" + dataHash + '\'' +
               '}';
    }
}
