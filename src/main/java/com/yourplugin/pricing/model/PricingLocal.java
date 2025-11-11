package com.yourplugin.pricing.model;

import java.util.Objects;

public class PricingLocal {
    public static final PricingLocal EMPTY = new PricingLocal(null, null, null, null, null);

    private final Double margin;
    private final Double tax;
    private final Double minPrice;
    private final Double maxPrice;
    private final Double volatility;

    public PricingLocal(Double margin, Double tax, Double minPrice, Double maxPrice, Double volatility) {
        this.margin = margin;
        this.tax = tax;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.volatility = volatility;
    }

    public Double getMargin() {
        return margin;
    }

    public Double getTax() {
        return tax;
    }

    public Double getMinPrice() {
        return minPrice;
    }

    public Double getMaxPrice() {
        return maxPrice;
    }

    public Double getVolatility() {
        return volatility;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PricingLocal that = (PricingLocal) o;
        return Objects.equals(margin, that.margin) && Objects.equals(tax, that.tax) && Objects.equals(minPrice, that.minPrice) && Objects.equals(maxPrice, that.maxPrice) && Objects.equals(volatility, that.volatility);
    }

    @Override
    public int hashCode() {
        return Objects.hash(margin, tax, minPrice, maxPrice, volatility);
    }

    @Override
    public String toString() {
        return "PricingLocal{" +
               "margin=" + margin +
               ", tax=" + tax +
               ", minPrice=" + minPrice +
               ", maxPrice=" + maxPrice +
               ", volatility=" + volatility +
               '}';
    }
}
