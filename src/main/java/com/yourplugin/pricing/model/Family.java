package com.yourplugin.pricing.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Family {
    private final ItemId rootItem;
    private final List<ItemId> variantItems;

    public Family(ItemId rootItem, List<ItemId> variantItems) {
        this.rootItem = Objects.requireNonNull(rootItem, "Root item cannot be null");
        this.variantItems = Collections.unmodifiableList(Objects.requireNonNull(variantItems, "Variant items list cannot be null"));
    }

    public ItemId getRootItem() {
        return rootItem;
    }

    public List<ItemId> getVariantItems() {
        return variantItems;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Family family = (Family) o;
        return rootItem.equals(family.rootItem) && variantItems.equals(family.variantItems);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rootItem, variantItems);
    }

    @Override
    public String toString() {
        return "Family{" +
               "rootItem=" + rootItem +
               ", variantItems=" + variantItems +
               '}';
    }
}
