package com.yourplugin.pricing.engine;

import com.yourplugin.pricing.model.GlobalPricingConfig;
import com.yourplugin.pricing.model.ItemConfig;
import com.yourplugin.pricing.model.PriceBreakdown;
import com.yourplugin.pricing.model.Recipe;
import com.yourplugin.pricing.model.RecipeType;
import com.yourplugin.pricing.service.PricingUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class PriceEngine {

    private final Map<String, ItemConfig> config;
    private final GlobalPricingConfig globalConfig;
    private final Logger logger;
    private final List<org.bukkit.inventory.Recipe> allBukkitRecipes;

    // Caches pour le calcul courant
    private final Map<String, Double> memo = new ConcurrentHashMap<>();
    private final Map<String, PriceBreakdown> breakdownCache = new ConcurrentHashMap<>();

    // Graphe de dépendances inversé pour les rebuilds partiels
    private final Map<String, Set<String>> reverseDeps = new ConcurrentHashMap<>();

    public PriceEngine(
            Map<String, ItemConfig> config,
            GlobalPricingConfig globalConfig,
            Logger logger,
            List<org.bukkit.inventory.Recipe> allBukkitRecipes) {
        this.config = Objects.requireNonNull(config, "Config cannot be null");
        this.globalConfig = Objects.requireNonNull(globalConfig, "GlobalConfig cannot be null");
        this.logger = Objects.requireNonNull(logger, "Logger cannot be null");
        this.allBukkitRecipes = Objects.requireNonNull(allBukkitRecipes, "Bukkit recipes cannot be null");
    }

    /**
     * Calcule TOUS les prix des items configurés.
     * C'est la méthode principale à appeler au démarrage.
     * @return Un snapshot immuable des prix calculés.
     */
    public Map<String, Double> calculateAllPrices() {
        memo.clear();
        breakdownCache.clear();
        buildReverseDependencies();

        for (String itemId : config.keySet()) {
            resolvePrice(itemId, new HashSet<>());
        }

        return Collections.unmodifiableMap(
                memo.entrySet().stream()
                        .filter(entry -> entry.getValue() != Double.POSITIVE_INFINITY)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
        );
    }

    private double resolvePrice(String itemId, Set<String> visiting) {
        String normalizedId = PricingUtils.normalize(itemId);

        // 1. Memoization cache hit
        if (memo.containsKey(normalizedId)) {
            return memo.get(normalizedId);
        }

        // 2. Cycle detection
        if (!visiting.add(normalizedId)) {
            logger.warning(String.format("[AutoPricing] Cycle détecté pour l'item : %s. La recette sera ignorée. Trace: %s", normalizedId, visiting));
            return Double.POSITIVE_INFINITY;
        }

        ItemConfig itemConfig = config.getOrDefault(normalizedId, new ItemConfig());

        // 3. Anchor (manual price defined in config)
        if (itemConfig.price().isPresent()) {
            double finalPrice = applyClampsAndVolatility(normalizedId, itemConfig.price().get(), itemConfig);
            memo.put(normalizedId, finalPrice);
            breakdownCache.put(normalizedId, new PriceBreakdown(normalizedId, finalPrice, itemConfig.price().get(), 0.0, 0.0, 0.0, 0.0, 0.0, true, null));
            visiting.remove(normalizedId);
            return finalPrice;
        }

        // 4. Calculate price from recipes
        List<Recipe> recipes = findRecipesFor(normalizedId);
        if (recipes.isEmpty()) {
            logger.finer(String.format("[AutoPricing] Pas de recette trouvée pour %s. Il ne peut être calculé.", normalizedId));
            visiting.remove(normalizedId);
            memo.put(normalizedId, Double.POSITIVE_INFINITY);
            return Double.POSITIVE_INFINITY;
        }

        double minPrice = Double.POSITIVE_INFINITY;
        PriceBreakdown bestBreakdown = null;

        for (Recipe recipe : recipes) {
            double rawCost = 0.0;
            boolean ingredientsAreValid = true;
            for (Map.Entry<String, Integer> ingredientEntry : recipe.ingredients().entrySet()) {
                String ingId = ingredientEntry.getKey();
                int qty = ingredientEntry.getValue();
                double ingPrice = resolvePrice(ingId, visiting);
                if (ingPrice == Double.POSITIVE_INFINITY) {
                    ingredientsAreValid = false;
                    break;
                }
                rawCost += ingPrice * qty;
            }

            if (!ingredientsAreValid) continue;

            // Apply full cost formula
            double energyCost = recipe.timeInSeconds() * globalConfig.machineTimeCostPerSecond() + recipe.fuelCost();
            
            // Handle amortization (for items like smithing templates that are consumed)
            double amortizationCost = 0.0;
            // if (itemConfig.amortize().isPresent()) {
            //     // Logic for amortization not fully implemented yet, as it requires knowing the item's purchase price
            //     // and how many crafts it yields. This is more complex and would need deeper integration.
            // }

            double unitBase = (rawCost + energyCost + amortizationCost) / recipe.resultQuantity();

            double margin = itemConfig.margin().orElse(globalConfig.margin());
            double tax = itemConfig.tax().orElse(globalConfig.tax());
            double priceWithMarginAndTax = unitBase * (1 + margin) * (1 + tax);

            if (priceWithMarginAndTax < minPrice) {
                minPrice = priceWithMarginAndTax;
                bestBreakdown = new PriceBreakdown(
                        normalizedId, 
                        0.0, // Final price will be set later after clamping
                        unitBase,
                        rawCost,
                        energyCost,
                        0.0, // Byproduct credit not implemented
                        margin,
                        tax,
                        false,
                        recipe
                );
            }
        }

        visiting.remove(normalizedId);

        if (bestBreakdown == null) { // No valid recipe found or all were INFINITY
            memo.put(normalizedId, Double.POSITIVE_INFINITY);
            return Double.POSITIVE_INFINITY;
        }

        double finalPrice = applyClampsAndVolatility(normalizedId, minPrice, itemConfig);
        memo.put(normalizedId, finalPrice);
        breakdownCache.put(normalizedId, bestBreakdown.withFinalPrice(finalPrice));

        return finalPrice;
    }

    private List<Recipe> findRecipesFor(String itemId) {
        Material material = Material.matchMaterial(itemId.replace("minecraft:", ""));
        if (material == null) return Collections.emptyList();

        List<Recipe> foundRecipes = new ArrayList<>();
        for (org.bukkit.inventory.Recipe bukkitRecipe : allBukkitRecipes) {
            if (bukkitRecipe.getResult().getType() == material) {
                Recipe internalRecipe = toInternalRecipe(bukkitRecipe);
                if (internalRecipe != null) {
                    foundRecipes.add(internalRecipe);
                }
            }
        }
        return foundRecipes;
    }

    private Recipe toInternalRecipe(org.bukkit.inventory.Recipe bukkitRecipe) {
        String resultId = bukkitRecipe.getResult().getType().getKey().toString();
        int resultQty = bukkitRecipe.getResult().getAmount();
        Map<String, Integer> ingredients = new HashMap<>();
        RecipeType type = RecipeType.CRAFTING;
        int timeInSeconds = 0;
        double fuelCost = 0.0;

        if (bukkitRecipe instanceof ShapedRecipe shapedRecipe) {
             shapedRecipe.getIngredientMap().values().stream()
                    .filter(Objects::nonNull)
                    .forEach(itemStack -> ingredients.merge(itemStack.getType().getKey().toString(), itemStack.getAmount(), Integer::sum));
        } else if (bukkitRecipe instanceof ShapelessRecipe shapelessRecipe) {
            shapelessRecipe.getIngredientList().stream()
                    .filter(Objects::nonNull)
                    .forEach(itemStack -> ingredients.merge(itemStack.getType().getKey().toString(), itemStack.getAmount(), Integer::sum));
        } else if (bukkitRecipe instanceof CookingRecipe<?> cookingRecipe) {
            ItemStack inputStack = cookingRecipe.getInput();
            if (inputStack.getType() != Material.AIR) {
                ingredients.put(inputStack.getType().getKey().toString(), inputStack.getAmount());
            }
            timeInSeconds = cookingRecipe.getCookingTime() / 20; // Ticks to seconds
            // TODO: Add fuel cost calculation if needed

            if (cookingRecipe instanceof org.bukkit.inventory.FurnaceRecipe) type = RecipeType.SMELTING;
            else if (cookingRecipe instanceof org.bukkit.inventory.BlastingRecipe) type = RecipeType.BLASTING;
            else if (cookingRecipe instanceof org.bukkit.inventory.SmokingRecipe) type = RecipeType.SMOKING;
            else if (cookingRecipe instanceof org.bukkit.inventory.CampfireRecipe) type = RecipeType.CAMPFIRE;

        } else {
            return null; // Ignore unsupported recipe types
        }

        if (ingredients.isEmpty()) return null;

        try {
            return new Recipe(resultId, resultQty, ingredients, type, timeInSeconds, fuelCost);
        } catch (IllegalArgumentException e) {
            logger.warning(String.format("[AutoPricing] Erreur en créant la recette pour %s: %s", resultId, e.getMessage()));
            return null;
        }
    }

    private double applyClampsAndVolatility(String itemId, double price, ItemConfig config) {
        double clampedPrice = price;
        if (config.minPrice().isPresent()) {
             clampedPrice = Math.max(clampedPrice, config.minPrice().get());
        }
        if (config.maxPrice().isPresent()) {
             clampedPrice = Math.min(clampedPrice, config.maxPrice().get());
        }
        // TODO: Implémenter la logique de lissage avec `volatility` si un ancien prix existe
        return clampedPrice;
    }

    // Method to create the reverse dependency graph (ingredient -> items that use it)
    private void buildReverseDependencies() {
        reverseDeps.clear();
        for (org.bukkit.inventory.Recipe bukkitRecipe : allBukkitRecipes) {
            Recipe internalRecipe = toInternalRecipe(bukkitRecipe);
            if (internalRecipe == null) continue;

            String resultId = PricingUtils.normalize(internalRecipe.resultId());
            for (String ingredientId : internalRecipe.ingredients().keySet()) {
                reverseDeps.computeIfAbsent(PricingUtils.normalize(ingredientId), k -> ConcurrentHashMap.newKeySet()).add(resultId);
            }
        }
    }

    public PriceBreakdown getBreakdown(String itemId) {
        // If breakdown is not in cache, resolve it (this will also populate memo)
        if (!breakdownCache.containsKey(itemId)) {
            resolvePrice(itemId, new HashSet<>());
        }
        return breakdownCache.get(itemId);
    }

    // Helper method for PriceBreakdown to set finalPrice (since records are immutable)
    private static PriceBreakdown withFinalPrice(PriceBreakdown original, double newFinalPrice) {
        return new PriceBreakdown(
                original.itemId(),
                newFinalPrice,
                original.basePrice(),
                original.rawMaterialsCost(),
                original.energyCost(),
                original.byproductCredit(),
                original.margin(),
                original.tax(),
                original.isAnchor(),
                original.recipeUsed()
        );
    }
}
