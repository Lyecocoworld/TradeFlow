package com.yourplugin.pricing.service;

import com.yourplugin.pricing.database.PriceDatabaseAPI;
import com.yourplugin.pricing.model.ItemId;
import com.yourplugin.pricing.model.PriceSnapshot;
import com.yourplugin.pricing.model.PricingData;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handles the initial seeding of calculated prices into the database and subsequent updates.
 * It respects existing database entries, manual overrides, and volatility clamps.
 */
public interface PriceImporter {

    /**
     * Performs an initial import of calculated prices into the database.
     * This should only happen if an item is not already present in the DB.
     * It should not overwrite manual anchors or newer prices.
     *
     * @param calculatedPrices The PriceSnapshot containing all newly calculated prices.
     * @param itemConfigs The ItemConfigs, used to check for manual anchors (SHOP).
     * @return A CompletableFuture that completes when the import process is finished.
     */
    CompletableFuture<Void> initialSeed(PriceSnapshot calculatedPrices, Map<ItemId, com.yourplugin.pricing.model.ItemConfig> itemConfigs);

    /**
     * Updates existing prices in the database based on a new PriceSnapshot.
     * This method should apply volatility clamps and respect manual overrides.
     *
     * @param newPrices The latest calculated PriceSnapshot.
     * @param oldPrices The previous PriceSnapshot (for volatility clamping).
     * @param itemConfigs The ItemConfigs, used to check for manual anchors (SHOP).
     * @return A CompletableFuture that completes when the update process is finished.
     */
    CompletableFuture<Void> updatePrices(PriceSnapshot newPrices, PriceSnapshot oldPrices, Map<ItemId, com.yourplugin.pricing.model.ItemConfig> itemConfigs);

    /**
     * Sets the database API implementation to use.
     * @param databaseAPI The PriceDatabaseAPI instance.
     */
    void setDatabaseAPI(PriceDatabaseAPI databaseAPI);
}
