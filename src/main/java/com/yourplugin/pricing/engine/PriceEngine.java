package com.yourplugin.pricing.engine;

import com.yourplugin.pricing.model.ItemConfig;
import com.yourplugin.pricing.model.PriceSnapshot;
import com.yourplugin.pricing.model.Recipe;

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
     * @return A CompletableFuture that will contain an immutable PriceSnapshot once calculations are complete.
     */
    CompletableFuture<PriceSnapshot> calculatePrices(Map<com.yourplugin.pricing.model.ItemId, ItemConfig> itemConfigs, List<Recipe> recipes);

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
    CompletableFuture<PriceSnapshot> recalculatePricesPartial(com.yourplugin.pricing.model.ItemId changedItemId, Map<com.yourplugin.pricing.model.ItemId, ItemConfig> itemConfigs, List<Recipe> recipes, PriceSnapshot currentSnapshot);

    /**
     * Returns the last calculated price snapshot.
     * @return The current PriceSnapshot.
     */
    PriceSnapshot getCurrentPriceSnapshot();
}