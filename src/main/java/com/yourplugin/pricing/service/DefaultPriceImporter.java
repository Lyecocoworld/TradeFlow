package com.yourplugin.pricing.service;

import com.yourplugin.pricing.database.PriceDatabaseAPI;
import com.yourplugin.pricing.model.ItemConfig;
import com.yourplugin.pricing.model.ItemId;
import com.yourplugin.pricing.model.PriceSnapshot;
import com.yourplugin.pricing.model.PricingData;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class DefaultPriceImporter implements PriceImporter {

    private static final Logger LOGGER = Logger.getLogger(DefaultPriceImporter.class.getName());
    private PriceDatabaseAPI databaseAPI;
    private final AuditService auditService;

    // Constants for pricing rules
    private static final double GLOBAL_PRICE_FLOOR = 0.01;

    public DefaultPriceImporter(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void setDatabaseAPI(PriceDatabaseAPI databaseAPI) {
        this.databaseAPI = Objects.requireNonNull(databaseAPI, "Database API cannot be null");
    }

    @Override
    public CompletableFuture<Void> initialSeed(PriceSnapshot calculatedPrices, Map<ItemId, ItemConfig> itemConfigs) {
        if (databaseAPI == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("DatabaseAPI not set for PriceImporter."));
        }

        LOGGER.info("Starting initial price seeding...");
        CompletableFuture<?>[] futures = calculatedPrices.getPrices().entrySet().stream()
                .filter(entry -> entry.getValue() != Double.POSITIVE_INFINITY) // Only seed priceable items
                .map(entry -> {
                    ItemId itemId = entry.getKey();
                    double price = entry.getValue();
                    ItemConfig config = itemConfigs.get(itemId);

                    // Generate a simple hash for now. In a real scenario, this would be more robust.
                    String dataHash = calculatedPrices.getBreakdown(itemId).map(b -> b.getStableHash()).orElse("no_breakdown");

                    PricingData newPricingData = new PricingData(itemId, price, config != null && config.getPricingLocal().getVolatility() != null ? config.getPricingLocal().getVolatility() : 0.0, System.currentTimeMillis(), dataHash);

                    return databaseAPI.itemExists(itemId).thenCompose(exists -> {
                        if (!exists) {
                            // Only seed if item is absent in DB
                            auditService.logInfo("Seeding initial price for " + itemId.getFullId() + " with price " + String.format("%.2f", price));
                            return databaseAPI.savePricingData(newPricingData);
                        } else {
                            // Item exists, do not overwrite during initial seed
                            return CompletableFuture.completedFuture(null);
                        }
                    });
                })
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures)
                .thenRun(() -> LOGGER.info("Initial price seeding complete."));
    }

    @Override
    public CompletableFuture<Void> updatePrices(PriceSnapshot newPrices, PriceSnapshot oldPrices, Map<ItemId, ItemConfig> itemConfigs) {
        if (databaseAPI == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("DatabaseAPI not set for PriceImporter."));
        }

        LOGGER.info("Starting price update...");
        CompletableFuture<?>[] futures = newPrices.getPrices().entrySet().stream()
                .filter(entry -> entry.getValue() != Double.POSITIVE_INFINITY) // Only update priceable items
                .map(entry -> {
                    ItemId itemId = entry.getKey();
                    double newCalculatedPrice = entry.getValue();
                    ItemConfig config = itemConfigs.get(itemId);

                    double finalPrice = newCalculatedPrice;
                    double volatility = config != null && config.getPricingLocal().getVolatility() != null ? config.getPricingLocal().getVolatility() : 0.0; // Default volatility

                    // Apply volatility clamp if old price exists and volatility is set
                    if (volatility > 0.0 && oldPrices.getPrice(itemId).isPresent()) {
                        double oldPrice = oldPrices.getPrice(itemId).get();
                        double maxDrop = oldPrice * volatility;
                        double minAllowedPrice = Math.max(oldPrice - maxDrop, GLOBAL_PRICE_FLOOR); // Clamp with global floor

                        if (newCalculatedPrice < minAllowedPrice) {
                            finalPrice = minAllowedPrice;
                            auditService.logInfo(String.format("Price clamp applied for %s: calculated %.2f, clamped to %.2f (max drop %.2f)",
                                    itemId.getFullId(), newCalculatedPrice, finalPrice, maxDrop));
                        }
                    }

                    final double effectiveFinalPrice = finalPrice;

                    String dataHash = newPrices.getBreakdown(itemId).map(b -> b.getStableHash()).orElse("no_breakdown");

                    PricingData updatedPricingData = new PricingData(itemId, effectiveFinalPrice, volatility, System.currentTimeMillis(), dataHash);

                    return databaseAPI.getPricingData(itemId).thenCompose(existingDataOpt -> {
                        if (existingDataOpt.isPresent()) {
                            PricingData existingData = existingDataOpt.get();
                            // Do not overwrite manual anchors (SHOP) or newer prices
                            // For simplicity, we'll assume if dataHash is different, it's a valid update.
                            // More robust logic would compare timestamps or explicit 'override' flags.
                            if (!existingData.getDataHash().equals(updatedPricingData.getDataHash())) {
                                auditService.logInfo("Updating price for " + itemId.getFullId() + " from " + String.format("%.2f", existingData.getPrice()) + " to " + String.format("%.2f", effectiveFinalPrice));
                                return databaseAPI.savePricingData(updatedPricingData);
                            } else {
                                return CompletableFuture.completedFuture(null);
                            }
                        } else {
                            // Should ideally not happen during an 'update' if initial seed was done.
                            // But if it does, save it.
                            auditService.logInfo("Saving new price during update for " + itemId.getFullId() + " with price " + String.format("%.2f", effectiveFinalPrice));
                            return databaseAPI.savePricingData(updatedPricingData);
                        }
                    });
                })
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures)
                .thenRun(() -> LOGGER.info("Price update complete."));
    }
}