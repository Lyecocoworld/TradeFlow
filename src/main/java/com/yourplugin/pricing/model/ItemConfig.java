package com.yourplugin.pricing.model;

import java.util.Optional;

public class ItemConfig {
    private final ItemId itemId;
    private final String section; // For GUI organization
    private final Double price; // Anchor price
    private final Integer maxBuy;
    private final Integer maxSell;
    private final Boolean free; // Allows price <= 0
    private final PricingLocal pricingLocal; // Local pricing parameters

    public ItemConfig(ItemId itemId, String section, Double price, Integer maxBuy, Integer maxSell, Boolean free, PricingLocal pricingLocal) {
        this.itemId = itemId;
        this.section = section;
        this.price = price;
        this.maxBuy = maxBuy;
        this.maxSell = maxSell;
        this.free = free;
        this.pricingLocal = pricingLocal != null ? pricingLocal : PricingLocal.EMPTY;
    }

    public ItemId getItemId() {
        return itemId;
    }

    public Optional<String> getSection() {
        return Optional.ofNullable(section);
    }

    public Optional<Double> getPrice() {
        return Optional.ofNullable(price);
    }

    public Optional<Integer> getMaxBuy() {
        return Optional.ofNullable(maxBuy);
    }

    public Optional<Integer> getMaxSell() {
        return Optional.ofNullable(maxSell);
    }

    public boolean isFree() {
        return free != null && free;
    }

    public PricingLocal getPricingLocal() {
        return pricingLocal;
    }

    // Builder or static factory methods could be added for easier construction
    // especially when merging configs.
}
