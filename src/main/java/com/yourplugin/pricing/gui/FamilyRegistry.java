package com.yourplugin.pricing.gui;

import com.yourplugin.pricing.model.Family;
import com.yourplugin.pricing.model.ItemId;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Stores and manages the family structure (root items and their variants).
 * Provides quick lookup for families and their members.
 */
public class FamilyRegistry {
    private final List<Family> families;
    private final Map<ItemId, Family> rootToFamilyMap; // For quick lookup of a family by its root
    private final Map<ItemId, ItemId> variantToRootMap; // For quick lookup of a variant's root

    public FamilyRegistry(List<Family> families) {
        this.families = Collections.unmodifiableList(families);
        this.rootToFamilyMap = families.stream()
                .collect(Collectors.toUnmodifiableMap(Family::getRootItem, f -> f));
        this.variantToRootMap = families.stream()
                .flatMap(family -> family.getVariantItems().stream().map(variant -> Map.entry(variant, family.getRootItem())))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue));
    }

    public List<Family> getAllFamilies() {
        return families;
    }

    public Optional<Family> getFamilyByRoot(ItemId rootItem) {
        return Optional.ofNullable(rootToFamilyMap.get(rootItem));
    }

    public Optional<ItemId> getRootOfVariant(ItemId variantItem) {
        return Optional.ofNullable(variantToRootMap.get(variantItem));
    }

    public boolean isRootItem(ItemId itemId) {
        return rootToFamilyMap.containsKey(itemId);
    }

    public boolean isVariantItem(ItemId itemId) {
        return variantToRootMap.containsKey(itemId);
    }

    public int getRootCount() {
        return families.size();
    }

    public int getVariantCount() {
        return (int) families.stream().flatMap(f -> f.getVariantItems().stream()).count();
    }

    // --- Sorting Heuristics ---

    /**
     * Determines a category for a given family based on its root item's key.
     * @param family The family to categorize.
     * @return A string representing the category (e.g., "wood", "metals").
     */
    public static String familyCategory(Family family) {
        String k = family.getRootItem().getKey();
        if (k.contains("_log") || k.contains("_planks")) return "wood";
        if (k.contains("iron") || k.contains("gold") || k.contains("copper")) return "metals";
        if (k.contains("stone") || k.contains("cobblestone") || k.contains("quartz")) return "stone";
        if (k.contains("_wool") || k.contains("_carpet") || k.contains("_dye")) return "wool";
        if (k.contains("redstone")) return "redstone";
        if (k.contains("bread") || k.contains("apple") || k.contains("meat")) return "food";
        return "misc";
    }

    private static final List<String> CAT_ORDER = List.of("wood","metals","stone","wool","redstone","food","misc");

    /**
     * Ranks a category for sorting purposes.
     * @param c The category string.
     * @return An integer rank, lower means higher priority.
     */
    public static int catRank(String c){
        int i = CAT_ORDER.indexOf(c);
        return i < 0 ? CAT_ORDER.size() : i;
    }

    /**
     * Comparator for sorting FamilyEntry objects based on category and then title.
     */
    public static final java.util.Comparator<Family> FAMILY_COMPARATOR =
            java.util.Comparator.comparing((Family fe) -> catRank(familyCategory(fe)))
                      .thenComparing(fe -> fe.getRootItem().getKey().toLowerCase());
}
