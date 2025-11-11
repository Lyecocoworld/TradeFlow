package com.yourplugin.pricing.model;

import java.util.Objects;

public record PriceBreakdown(
    String itemId,
    double finalPrice,
    double basePrice,
    double rawMaterialsCost,
    double energyCost,
    double byproductCredit,
    double margin,
    double tax,
    boolean isAnchor,
    Recipe recipeUsed
) {
    public PriceBreakdown {
        Objects.requireNonNull(itemId, "itemId cannot be null");
    }

    /**
     * Creates a new PriceBreakdown instance with an updated finalPrice.
     * Records are immutable, so this returns a new instance.
     * @param newFinalPrice The new final price to set.
     * @return A new PriceBreakdown instance with the updated finalPrice.
     */
    public PriceBreakdown withFinalPrice(double newFinalPrice) {
        return new PriceBreakdown(
                this.itemId,
                newFinalPrice,
                this.basePrice,
                this.rawMaterialsCost,
                this.energyCost,
                this.byproductCredit,
                this.margin,
                this.tax,
                this.isAnchor,
                this.recipeUsed
        );
    }
}