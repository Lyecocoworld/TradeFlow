package com.yourplugin.pricing.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class PriceSnapshot {
    private final Map<ItemId, Double> prices;
    private final Map<ItemId, Breakdown> breakdowns;

    public PriceSnapshot(Map<ItemId, Double> prices, Map<ItemId, Breakdown> breakdowns) {
        this.prices = Collections.unmodifiableMap(Objects.requireNonNull(prices, "Prices map cannot be null"));
        this.breakdowns = Collections.unmodifiableMap(Objects.requireNonNull(breakdowns, "Breakdowns map cannot be null"));
    }

    public Optional<Double> getPrice(ItemId itemId) {
        return Optional.ofNullable(prices.get(itemId));
    }

    public Optional<Breakdown> getBreakdown(ItemId itemId) {
        return Optional.ofNullable(breakdowns.get(itemId));
    }

    public Map<ItemId, Double> getPrices() {
        return prices;
    }

    public Map<ItemId, Breakdown> getBreakdowns() {
        return breakdowns;
    }

    public boolean isEmpty() {
        return prices.isEmpty();
    }

    @Override
    public String toString() {
        return "PriceSnapshot{" +
               "prices.size=" + prices.size() +
               ", breakdowns.size=" + breakdowns.size() +
               '}';
    }
}