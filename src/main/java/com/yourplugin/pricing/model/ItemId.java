package com.yourplugin.pricing.model;

import java.util.Objects;

public class ItemId {
    private final String namespace; // e.g., "minecraft"
    private final String key;       // e.g., "iron_ingot"

    public ItemId(String namespace, String key) {
        this.namespace = Objects.requireNonNull(namespace, "Namespace cannot be null");
        this.key = Objects.requireNonNull(key, "Key cannot be null");
    }

    public ItemId(String fullId) {
        Objects.requireNonNull(fullId, "Full ID cannot be null");
        if (!fullId.contains(":")) {
            this.namespace = "minecraft"; // Default to minecraft namespace
            this.key = fullId;
        } else {
            String[] parts = fullId.split(":", 2);
            this.namespace = parts[0];
            this.key = parts[1];
        }
    }

    public String getNamespace() {
        return namespace;
    }

    public String getKey() {
        return key;
    }

    public String getFullId() {
        return namespace + ":" + key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ItemId itemId = (ItemId) o;
        return namespace.equals(itemId.namespace) && key.equals(itemId.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, key);
    }

    @Override
    public String toString() {
        return getFullId();
    }
}
