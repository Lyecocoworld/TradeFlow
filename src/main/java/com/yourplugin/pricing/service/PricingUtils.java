package com.yourplugin.pricing.service;

import java.util.Objects;

public class PricingUtils {

    private PricingUtils() {
        // Utility class
    }

    /**
     * Normalizes an item ID to the format "minecraft:item_id".
     * @param itemId The item ID to normalize.
     * @return The normalized item ID.
     */
    public static String normalize(String itemId) {
        Objects.requireNonNull(itemId, "Item ID cannot be null");
        if (itemId.contains(":")) {
            return itemId.toLowerCase();
        } else {
            return "minecraft:" + itemId.toLowerCase();
        }
    }
}
