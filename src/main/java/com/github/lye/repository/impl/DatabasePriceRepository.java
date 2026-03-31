package com.github.lye.repository.impl;

import com.github.lye.repository.PriceRepository;
import com.github.lye.pricing.database.PriceDatabaseAPI;
import com.github.lye.pricing.model.ItemId;

/**
 * Adapter that exposes {@link PriceDatabaseAPI} as a synchronous {@link PriceRepository}.
 * This is deliberately simple: callers that need full async control should still use the API directly.
 */
public class DatabasePriceRepository implements PriceRepository {

    private final PriceDatabaseAPI priceDatabaseAPI;

    public DatabasePriceRepository(PriceDatabaseAPI priceDatabaseAPI) {
        this.priceDatabaseAPI = priceDatabaseAPI;
    }

    @Override
    public Double getPriceOrNull(ItemId itemId) {
        // PriceDatabaseAPI already exposes a synchronous helper for this use case.
        return priceDatabaseAPI.getOrNull(itemId.getKey());
    }

    @Override
    public void upsertPrice(ItemId itemId, double price) {
        priceDatabaseAPI.upsert(itemId.getKey(), price);
    }
}

