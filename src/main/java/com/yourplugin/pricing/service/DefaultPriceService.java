package com.yourplugin.pricing.service;

import com.yourplugin.pricing.database.PriceDatabaseAPI;
import com.yourplugin.pricing.model.ItemId;
import com.yourplugin.pricing.model.PriceSnapshot;

import java.util.Collections;
import java.util.Optional;
import java.util.logging.Logger;

public class DefaultPriceService implements PriceService {

    private static final Logger LOGGER = Logger.getLogger(DefaultPriceService.class.getName());
    private volatile PriceSnapshot currentSnapshot = new PriceSnapshot(Collections.emptyMap(), Collections.emptyMap());
    private final PriceDatabaseAPI database;

    public DefaultPriceService(PriceDatabaseAPI database) {
        this.database = database;
    }

    @Override
    public Optional<Double> getPrice(ItemId itemId) {
        return currentSnapshot.getPrice(itemId);
    }

    @Override
    public double bestPrice(ItemId itemId) {
        Double price = bestPriceOrNull(itemId);
        return price != null ? price : Double.NaN;
    }

    @Override
    public Double bestPriceOrNull(ItemId id) {
        final String k = id.getKey(); // Assuming ItemId.getKey() returns the raw key
        Double d = database.getOrNull(k);
        if (d != null && d > 0.0 && !d.isInfinite()) return d;

        Double s = currentSnapshot.getPrice(id).orElse(null);
        if (s != null && s > 0.0 && !s.isInfinite()) return s;

        return null;
    }

    @Override
    public void updatePriceSnapshot(PriceSnapshot newSnapshot) {
        this.currentSnapshot = newSnapshot;
        LOGGER.info(String.format("PriceService updated with new snapshot containing %d prices.", newSnapshot.getPrices().size()));
    }

    @Override
    public PriceSnapshot getCurrentSnapshot() {
        return currentSnapshot;
    }
}