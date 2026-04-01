package com.github.lye.pricing.engine;

import com.github.lye.pricing.model.ItemConfig;
import com.github.lye.pricing.model.PriceSnapshot;
import com.github.lye.pricing.model.Recipe;

import com.github.lye.data.Shop;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The core engine responsible for calculating and providing item prices.
 * It takes item configurations (anchors) and recipes to build a price graph.
 */
public interface PriceEngine {

    /**
     * Asynchronously calculates all item prices based on provided configurations and recipes.
     * This method should handle cycle detection, invalid anchors, and apply pricing rules.
     *
     * @param itemConfigs A map of ItemId to ItemConfig, representing anchor prices and item properties.
     * @param recipes A list of all available recipes.
     * @param loadedShops A map of runtime shop data (buy/sell history) for dynamic pricing.
     * @return A CompletableFuture that will contain an immutable PriceSnapshot once calculations are complete.
     */
    CompletableFuture<PriceSnapshot> calculatePrices(Map<com.github.lye.pricing.model.ItemId, ItemConfig> itemConfigs, List<Recipe> recipes, Map<String, Shop> loadedShops);

    /**
     * Triggers a partial recalculation for a specific item and its dependents.
     * This is useful when an anchor price changes.
     *
     * @param changedItemId The ItemId that has been updated.
     * @param itemConfigs The current map of ItemId to ItemConfig.
     * @param recipes The current list of all available recipes.
     * @param currentSnapshot The previously calculated PriceSnapshot.
     * @return A CompletableFuture that will contain a new PriceSnapshot with updated prices.
     */
    CompletableFuture<PriceSnapshot> recalculatePricesPartial(com.github.lye.pricing.model.ItemId changedItemId, Map<com.github.lye.pricing.model.ItemId, ItemConfig> itemConfigs, List<Recipe> recipes, PriceSnapshot currentSnapshot);

    /**
     * Returns the last calculated price snapshot.
     * @return The current PriceSnapshot.
     */
    PriceSnapshot getCurrentPriceSnapshot();
}
