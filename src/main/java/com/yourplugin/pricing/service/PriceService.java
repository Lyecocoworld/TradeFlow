package com.yourplugin.pricing.service;

import com.yourplugin.pricing.model.ItemId;
import com.yourplugin.pricing.model.PriceSnapshot;

import java.util.Optional;

/**
 * Provides O(1) access to item prices from the current PriceSnapshot.
 * This is the primary interface for other parts of the plugin to query prices.
 */
public interface PriceService {

    /**
     * Retrieves the current price for a given ItemId.
     * @param itemId The ID of the item.
     * @return An Optional containing the price (double) if available, or empty if the item is unpriceable.
     */
    Optional<Double> getPrice(ItemId itemId);

    /**
     * Retrieves the best available price for a given ItemId.
     * Returns Double.NaN if the item is unpriceable.
     * @param itemId The ID of the item.
     * @return The price (double) or Double.NaN if unavailable.
     */
    double bestPrice(ItemId itemId);

    /**
     * Retrieves the best available price for a given ItemId, returning null if unavailable.
     * This method first checks the database, then the current snapshot.
     * @param id The ID of the item.
     * @return The price (Double) or null if unavailable.
     */
    Double bestPriceOrNull(ItemId id);

    /**
     * Updates the internal price snapshot. This should be called after a full or partial price recalculation.
     * @param newSnapshot The new PriceSnapshot to use.
     */
    void updatePriceSnapshot(PriceSnapshot newSnapshot);

    /**
     * Returns the current price snapshot.
     * @return The current PriceSnapshot.
     */
    PriceSnapshot getCurrentSnapshot();
}
