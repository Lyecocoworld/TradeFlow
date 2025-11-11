package com.yourplugin.pricing.model;

import java.util.Objects;

public class QItem {
    private final ItemId item;
    private final double qty;

    public QItem(ItemId item, double qty) {
        this.item = Objects.requireNonNull(item, "Item ID cannot be null");
        if (qty <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.qty = qty;
    }

    public ItemId getItem() {
        return item;
    }

    public double getQty() {
        return qty;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QItem qItem = (QItem) o;
        return Double.compare(qItem.qty, qty) == 0 && item.equals(qItem.item);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, qty);
    }

    @Override
    public String toString() {
        return "QItem{" +
               "item=" + item +
               ", qty=" + qty +
               '}';
    }
}
