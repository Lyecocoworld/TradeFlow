package com.yourplugin.pricing.service;

import com.yourplugin.pricing.engine.PriceEngine;
import com.yourplugin.pricing.model.PriceBreakdown;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class PriceService {

    private final PriceEngine engine;
    private final AtomicReference<Map<String, Double>> priceSnapshot = new AtomicReference<>(Collections.emptyMap());

    public PriceService(PriceEngine engine) {
        this.engine = Objects.requireNonNull(engine, "PriceEngine cannot be null");
    }

    public Double getPrice(String itemId) {
        return priceSnapshot.get().get(PricingUtils.normalize(itemId));
    }

    public PriceBreakdown getPriceBreakdown(String itemId) {
        return engine.getBreakdown(PricingUtils.normalize(itemId));
    }

    public void updateSnapshot(Map<String, Double> newPrices) {
        Objects.requireNonNull(newPrices, "New prices map cannot be null");
        priceSnapshot.set(Collections.unmodifiableMap(newPrices));
    }
}
