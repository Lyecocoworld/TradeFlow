package com.yourplugin.pricing.service;

import com.yourplugin.pricing.model.ItemConfig;
import com.yourplugin.pricing.model.ItemId;
import com.yourplugin.pricing.model.PriceSnapshot;
import com.yourplugin.pricing.model.Recipe;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DefaultAuditService implements AuditService {

    private final Logger logger;

    public DefaultAuditService(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void auditStartup(Map<ItemId, ItemConfig> itemConfigs, List<Recipe> allRecipes, List<Recipe> filteredRecipes, List<ItemId> unpriceableItems) {
        logger.info("--- Auto-Pricing System Startup Audit ---");
        logger.info(String.format("Loaded %d item configurations.", itemConfigs.size()));
        logger.info(String.format("Loaded %d total recipes, %d filtered recipes.", allRecipes.size(), filteredRecipes.size()));
        if (!unpriceableItems.isEmpty()) {
            logger.warning(String.format("Found %d unpriceable items: %s", unpriceableItems.size(), unpriceableItems));
        }
        logger.info("-----------------------------------------");
    }

    @Override
    public void logGraphAndFamilyStats(int totalItems, int cyclesDetected, int familiesCount, int variantsCount) {
        logger.info(String.format("[Auto-Tune] Built dependency graph: %d items, %d reversible cycles.", totalItems, cyclesDetected));
        logger.info(String.format("[Auto-Tune] Families: %d roots, %d variants.", familiesCount, variantsCount));
    }

    @Override
    public void logDatabaseStatus(boolean isMySQLActive) {
        logger.info(String.format("[Auto-Tune] MySQL %s, MapDB in standby", isMySQLActive ? "active" : "inactive"));
    }

    @Override
    public void logGuiInitialization(int pages, int families) {
        logger.info(String.format("[Auto-Tune] GUI initialized: %d pages, %d families", pages, families));
    }

    @Override
    public void logWarning(String message) {
        logger.warning("[Auto-Tune] " + message);
    }

    @Override
    public void logInfo(String message) {
        logger.info("[Auto-Tune] " + message);
    }

    // New audit methods for commands

    /**
     * Logs anchors with price <= 0 and no free:true.
     * @param itemConfigs All item configurations.
     */
    public void auditInvalidAnchors(Map<ItemId, ItemConfig> itemConfigs) {
        logger.info("--- Invalid Anchors Audit ---");
        itemConfigs.values().stream()
                .filter(config -> config.getPrice().isPresent() && config.getPrice().get() <= 0 && !config.isFree())
                .forEach(config -> logger.info("Invalid anchor: " + config.getItemId().getFullId() + " (price=" + config.getPrice().get() + ", free=" + config.isFree() + ")"));
        logger.info("-----------------------------");
    }

    /**
     * Logs items with infinite prices.
     * @param priceSnapshot The current price snapshot.
     */
    public void auditInfinitePrices(PriceSnapshot priceSnapshot) {
        logger.info("--- Infinite Prices Audit ---");
        priceSnapshot.getPrices().entrySet().stream()
                .filter(entry -> Double.isInfinite(entry.getValue()))
                .forEach(entry -> logger.info("Infinite price: " + entry.getKey().getFullId()));
        logger.info("-----------------------------");
    }

    /**
     * Logs a summary of excluded recipes by filter type.
     * @param excludedRecipesLog The set of excluded recipe logs from RecipeFilter.
     */
    public void auditExcludedRecipesSummary(Set<String> excludedRecipesLog) {
        logger.info("--- Excluded Recipes Summary ---");
        excludedRecipesLog.forEach(logger::info);
        logger.info("--------------------------------");
    }
}