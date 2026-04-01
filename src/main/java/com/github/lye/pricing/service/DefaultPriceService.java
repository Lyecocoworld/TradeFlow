package com.github.lye.pricing.service;

import com.github.lye.repository.PriceRepository;
import com.github.lye.pricing.model.ItemId;
import com.github.lye.pricing.model.PriceSnapshot;

import java.util.Collections;
import java.util.Optional;

public class DefaultPriceService implements PriceService {

    private volatile PriceSnapshot currentSnapshot = new PriceSnapshot(Collections.emptyMap(), Collections.emptyMap());
    private final PriceRepository priceRepository;

    public DefaultPriceService(PriceRepository priceRepository) {
        this.priceRepository = priceRepository;
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
        // First try DB-backed repository
        Double d = priceRepository.getPriceOrNull(id);
        if (d != null && d > 0.0 && !d.isInfinite()) {
            return d;
        }

        // Then fall back to the current in-memory snapshot
        Double s = currentSnapshot.getPrice(id).orElse(null);
        if (s != null && s > 0.0 && !s.isInfinite()) {
            return s;
        }

        return null;
    }

    @Override
    public void updatePriceSnapshot(PriceSnapshot newSnapshot) {
        this.currentSnapshot = newSnapshot;
    }

    @Override
    public PriceSnapshot getCurrentSnapshot() {
        return currentSnapshot;
    }
}
