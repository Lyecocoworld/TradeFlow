package com.yourplugin.pricing.database;

import com.yourplugin.pricing.model.ItemId;
import com.yourplugin.pricing.model.PricingData;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for interacting with the existing dual database system (MySQL + MapDB fallback).
 * All operations should ideally be asynchronous to be Folia-safe.
 */
public interface PriceDatabaseAPI {

    /**
     * Retrieves pricing data for a given ItemId asynchronously.
     * @param itemId The ID of the item.
     * @return A CompletableFuture that will contain an Optional<PricingData>.
     */
    CompletableFuture<Optional<PricingData>> getPricingData(ItemId itemId);

    /**
     * Saves or updates pricing data for an item asynchronously.
     * @param pricingData The pricing data to save.
     * @return A CompletableFuture that completes when the operation is done.
     */
    CompletableFuture<Void> savePricingData(PricingData pricingData);

    /**
     * Checks if pricing data for a given ItemId exists in the database asynchronously.
     * @param itemId The ID of the item.
     * @return A CompletableFuture that will contain a boolean indicating existence.
     */
    CompletableFuture<Boolean> itemExists(ItemId itemId);

    /**
     * Retrieves the price for a given key, or null if not found.
     * This method handles key normalization internally.
     * @param anyKey The item key, with or without "minecraft:" prefix.
     * @return The price (Double) or null if not found.
     */
    Double getOrNull(String anyKey);

    /**
     * Inserts or updates a price for a given key.
     * This method handles key normalization internally.
     * @param anyKey The item key, with or without "minecraft:" prefix.
     * @param price The price to set.
     */
    void upsert(String anyKey, double price);

    /**
     * Initializes the database connection(s).
     * @return A CompletableFuture that completes when initialization is done.
     */
    CompletableFuture<Void> initialize();

    /**
     * Shuts down the database connection(s).
     * @return A CompletableFuture that completes when shutdown is done.
     */
    CompletableFuture<Void> shutdown();
}
