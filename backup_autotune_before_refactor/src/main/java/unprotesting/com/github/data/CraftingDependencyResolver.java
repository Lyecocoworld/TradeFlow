package unprotesting.com.github.data;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.Material;
import org.bukkit.inventory.RecipeChoice;
import unprotesting.com.github.util.AutoTuneLogger;
import unprotesting.com.github.util.Format;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class CraftingDependencyResolver {

    public static class RecipeData {
        public final List<String> ingredients;
        public final int yield;

        public RecipeData(List<String> ingredients, int yield) {
            this.ingredients = ingredients;
            this.yield = yield;
        }
    }

    private final Map<String, List<RecipeData>> dependencyGraph = new HashMap<>();
    private final AutoTuneLogger logger = Format.getLog();

    public List<RecipeData> getRecipeData(String itemName) {
        return dependencyGraph.get(itemName);
    }

    public void buildDependencyGraph() {
        dependencyGraph.clear();
        Iterator<Recipe> recipeIterator = Bukkit.recipeIterator();

        while (recipeIterator.hasNext()) {
            Recipe recipe = recipeIterator.next();
            ItemStack result = recipe.getResult();
            String resultName = result.getType().toString().toLowerCase();

            List<String> ingredients = new ArrayList<>();
            if (recipe instanceof ShapedRecipe) {
                ShapedRecipe shapedRecipe = (ShapedRecipe) recipe;
                for (ItemStack ingredient : shapedRecipe.getIngredientMap().values()) {
                    if (ingredient != null) {
                        ingredients.add(ingredient.getType().toString().toLowerCase());
                    }
                }
            } else if (recipe instanceof ShapelessRecipe) {
                ShapelessRecipe shapelessRecipe = (ShapelessRecipe) recipe;
                for (ItemStack ingredient : shapelessRecipe.getIngredientList()) {
                    if (ingredient != null) {
                        ingredients.add(ingredient.getType().toString().toLowerCase());
                    }
                }
            } else if (recipe instanceof FurnaceRecipe) {
                FurnaceRecipe furnaceRecipe = (FurnaceRecipe) recipe;
                Material ingredientMaterial = furnaceRecipe.getInput().getType();
                if (ingredientMaterial != null) {
                    ingredients.add(ingredientMaterial.toString().toLowerCase());
                }
            }

            if (!ingredients.isEmpty()) {
                dependencyGraph.computeIfAbsent(resultName, k -> new ArrayList<>())
                        .add(new RecipeData(ingredients, result.getAmount()));
            }
        }
        logger.info("Built crafting dependency graph with " + dependencyGraph.size() + " items.");
    }

    public void updateCraftedItemPrices(String changedItem) {
        updateCraftedItemPrices(changedItem, new java.util.HashSet<>());
    }

    private void updateCraftedItemPrices(String changedItem, java.util.Set<String> updatedItems) {
        if (updatedItems.contains(changedItem)) {
            return; // Already updated in this cycle, prevent recursion
        }
        updatedItems.add(changedItem);

        for (Map.Entry<String, List<RecipeData>> entry : dependencyGraph.entrySet()) {
            String craftedItem = entry.getKey();
            boolean usesChangedItem = false;
            for (RecipeData recipeData : entry.getValue()) {
                if (recipeData.ingredients.contains(changedItem)) {
                    usesChangedItem = true;
                    break;
                }
            }

            if (usesChangedItem) {
                // This item is crafted using the changed item.
                // We need to recalculate its price.
                double newPrice = calculatePrice(craftedItem);
                if (newPrice >= 0) { // Only update if price is valid
                    Shop shop = ShopUtil.getShop(craftedItem, false);
                    if (shop != null) {
                        shop.setPrice(newPrice);
                        ShopUtil.putShop(craftedItem, shop);
                        // Recursively update the prices of items that depend on this item.
                        updateCraftedItemPrices(craftedItem, updatedItems);
                    }
                }
            }
        }
    }

    public void calculateAllCraftedItemPrices() {
        logger.info("Calculating initial prices for all crafted items...");
        for (String craftedItem : dependencyGraph.keySet()) {
            double newPrice = calculatePrice(craftedItem);
            if (newPrice >= 0) {
                Shop shop = ShopUtil.getShop(craftedItem, false);
                if (shop != null) {
                    shop.setPrice(newPrice);
                    ShopUtil.putShop(craftedItem, shop);
                }
            }
        }
        logger.info("Finished calculating initial crafted item prices.");
    }

    public double calculatePrice(String itemName) {
        return calculatePrice(itemName, new java.util.HashSet<>());
    }

    private double calculatePrice(String itemName, java.util.Set<String> recursionStack) {
        if (recursionStack.contains(itemName)) {
            logger.warning("Circular dependency detected in crafting recipe for: " + itemName);
            return -1;
        }

        List<RecipeData> recipeDataList = dependencyGraph.get(itemName);
        if (recipeDataList == null) {
            // This is a raw material, its price is not calculated.
            Shop shop = ShopUtil.getShop(itemName, true);
            return shop != null ? shop.getPrice() : -1; // Return -1 if shop not found
        }

        recursionStack.add(itemName);

        double minPrice = Double.MAX_VALUE;

        for (RecipeData recipeData : recipeDataList) {
            double currentRecipePrice = 0;
            boolean allIngredientsAvailable = true;

            for (String ingredientName : recipeData.ingredients) {
                double ingredientPrice = calculatePrice(ingredientName, recursionStack); // Recursively calculate price
                if (ingredientPrice < 0) {
                    allIngredientsAvailable = false;
                    break;
                }
                currentRecipePrice += ingredientPrice;
            }

            if (allIngredientsAvailable) {
                double pricePerItem = currentRecipePrice / recipeData.yield;
                if (pricePerItem < minPrice) {
                    minPrice = pricePerItem;
                }
            }
        }

        recursionStack.remove(itemName);

        if (minPrice == Double.MAX_VALUE) {
            // logger.warning("Could not determine a valid recipe for: " + itemName);
            return -1;
        }

        return minPrice;
    }
}