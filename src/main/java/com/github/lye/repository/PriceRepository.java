package com.github.lye.repository;

import com.github.lye.pricing.model.ItemId;

/**
 * Repository abstraction for persisted prices.
 * Sits on top of the concrete PriceDatabaseAPI implementation.
 */
public interface PriceRepository {

    /**
     * Reads a price for the given item identifier or returns {@code null}.
     */
    Double getPriceOrNull(ItemId itemId);

    /**
     * Upserts a price for the given item identifier.
     */
    void upsertPrice(ItemId itemId, double price);
}

