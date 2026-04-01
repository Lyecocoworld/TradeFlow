package com.github.lye.license;

import java.util.List;
import java.util.Map;

public class License {
    private final String id;
    private final String name;
    private final int durationDays;
    private final double price;
    private final Map<String, Double> effects; // buy_discount, sell_bonus, etc.
    private final List<String> categories; // list of section names or "all"
    private final List<String> lore;

    public License(String id, String name, int durationDays, double price, Map<String, Double> effects, List<String> categories, List<String> lore) {
        this.id = id;
        this.name = name;
        this.durationDays = durationDays;
        this.price = price;
        this.effects = effects;
        this.categories = categories;
        this.lore = lore;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getDurationDays() { return durationDays; }
    public double getPrice() { return price; }
    public Map<String, Double> getEffects() { return effects; }
    public List<String> getCategories() { return categories; }
    public List<String> getLore() { return lore; }

    public double getEffect(String type, double defaultValue) {
        return effects.getOrDefault(type, defaultValue);
    }

    public boolean appliesTo(String section) {
        if (categories.contains("all")) return true;
        return categories.contains(section.toLowerCase());
    }
}
