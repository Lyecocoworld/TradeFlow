package com.yourplugin.pricing.service;

import com.yourplugin.pricing.model.ItemId;
import com.yourplugin.pricing.model.PriceSnapshot;
import com.yourplugin.pricing.model.Recipe;

import java.util.List;
import java.util.Map;

/**
 * Service for auditing and logging various aspects of the pricing system,
 * including invalid configurations, unpriceable items, and detected cycles.
 */
public interface AuditService {

    /**
     * Performs an audit of the initial configuration and recipes.
     * Logs invalid anchors, excluded recipes, and other potential issues.
     *
     * @param itemConfigs All loaded item configurations.
     * @param allRecipes All loaded recipes.
     * @param filteredRecipes Recipes after applying filters.
     * @param unpriceableItems Items that could not be priced.
     */
    void auditStartup(Map<ItemId, com.yourplugin.pricing.model.ItemConfig> itemConfigs, List<Recipe> allRecipes, List<Recipe> filteredRecipes, List<ItemId> unpriceableItems);

    /**
     * Logs information about the built dependency graph and families.
     * @param totalItems The total number of items in the dependency graph.
     * @param cyclesDetected The number of cycles detected (should be 0 after filtering).
     * @param familiesCount The number of families loaded.
     * @param variantsCount The number of variants across all families.
     */
    void logGraphAndFamilyStats(int totalItems, int cyclesDetected, int familiesCount, int variantsCount);

    /**
     * Logs the status of the database connection.
     * @param isMySQLActive True if MySQL is active, false otherwise.
     */
    void logDatabaseStatus(boolean isMySQLActive);

    /**
     * Logs the initialization status of the GUI.
     * @param pages The number of pages in the GUI.
     * @param families The number of families displayed.
     */
    void logGuiInitialization(int pages, int families);

    /**
     * Logs a specific warning or error related to pricing.
     * @param message The warning/error message.
     */
    void logWarning(String message);

    /**
     * Logs a specific info message related to pricing.
     * @param message The info message.
     */
    void logInfo(String message);

    void auditInvalidAnchors(java.util.Map<com.yourplugin.pricing.model.ItemId, com.yourplugin.pricing.model.ItemConfig> itemConfigs);
    void auditInfinitePrices(com.yourplugin.pricing.model.PriceSnapshot priceSnapshot);
    void auditExcludedRecipesSummary(java.util.Set<String> excludedRecipesLog);
}
