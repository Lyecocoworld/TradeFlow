package com.yourplugin.pricing.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class Recipe {
    private final RecipeType type;
    private final ItemId outputItem;
    private final double outputQuantity;
    private final List<QItem> inputs;
    private final List<QItem> byproducts;
    private final double fuelCost;
    private final double seconds;

    private Recipe(Builder builder) {
        this.type = Objects.requireNonNull(builder.type, "Recipe type cannot be null");
        this.outputItem = Objects.requireNonNull(builder.outputItem, "Output item cannot be null");
        if (builder.outputQuantity <= 0) {
            throw new IllegalArgumentException("Output quantity must be positive");
        }
        this.outputQuantity = builder.outputQuantity;
        this.inputs = Collections.unmodifiableList(Objects.requireNonNull(builder.inputs, "Inputs list cannot be null"));
        this.byproducts = Collections.unmodifiableList(Objects.requireNonNull(builder.byproducts, "Byproducts list cannot be null"));
        this.fuelCost = builder.fuelCost;
        this.seconds = builder.seconds;
    }

    public static Builder builder() {
        return new Builder();
    }

    public RecipeType getType() {
        return type;
    }

    public ItemId getOutputItem() {
        return outputItem;
    }

    public double getOutputQuantity() {
        return outputQuantity;
    }

    public List<QItem> getInputs() {
        return inputs;
    }

    public Map<ItemId, Double> getIngredients() {
        return inputs.stream().collect(Collectors.toMap(QItem::getItem, QItem::getQty));
    }

    public List<QItem> getByproducts() {
        return byproducts;
    }

    public double getFuelCost() {
        return fuelCost;
    }

    public double getSeconds() {
        return seconds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Recipe recipe = (Recipe) o;
        return Double.compare(recipe.outputQuantity, outputQuantity) == 0 && Double.compare(recipe.fuelCost, fuelCost) == 0 && Double.compare(recipe.seconds, seconds) == 0 && type == recipe.type && outputItem.equals(recipe.outputItem) && inputs.equals(recipe.inputs) && byproducts.equals(recipe.byproducts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, outputItem, outputQuantity, inputs, byproducts, fuelCost, seconds);
    }

    @Override
    public String toString() {
        return "Recipe{" +
               "type=" + type +
               ", outputItem=" + outputItem +
               ", outputQuantity=" + outputQuantity +
               ", inputs=" + inputs +
               ", byproducts=" + byproducts +
               ", fuelCost=" + fuelCost +
               ", seconds=" + seconds +
               '}';
    }

    public static class Builder {
        private RecipeType type;
        private ItemId outputItem;
        private double outputQuantity;
        private List<QItem> inputs = Collections.emptyList();
        private List<QItem> byproducts = Collections.emptyList();
        private double fuelCost;
        private double seconds;

        public Builder type(RecipeType type) {
            this.type = type;
            return this;
        }

        public Builder output(ItemId outputItem) {
            this.outputItem = outputItem;
            return this;
        }

        public Builder outQty(double outputQuantity) {
            this.outputQuantity = outputQuantity;
            return this;
        }

        public Builder inputs(List<QItem> inputs) {
            this.inputs = inputs;
            return this;
        }

        public Builder byproducts(List<QItem> byproducts) {
            this.byproducts = byproducts;
            return this;
        }

        public Builder fuelCost(double fuelCost) {
            this.fuelCost = fuelCost;
            return this;
        }

        public Builder seconds(double seconds) {
            this.seconds = seconds;
            return this;
        }

        public Recipe build() {
            return new Recipe(this);
        }
    }
}
