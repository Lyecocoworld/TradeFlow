package com.yourplugin.pricing.model;

import java.util.Map;
import java.util.Objects;

public record Recipe(
    String resultId,
    int resultQuantity,
    Map<String, Integer> ingredients,
    RecipeType type,
    int timeInSeconds,
    double fuelCost
) {
    public Recipe {
        Objects.requireNonNull(resultId, "resultId cannot be null");
        Objects.requireNonNull(ingredients, "ingredients cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        if (resultQuantity <= 0) {
            throw new IllegalArgumentException("resultQuantity must be positive");
        }
    }

    // Default constructor for CRAFTING type
    public Recipe(String resultId, int resultQuantity, Map<String, Integer> ingredients) {
        this(resultId, resultQuantity, ingredients, RecipeType.CRAFTING, 0, 0.0);
    }
}
