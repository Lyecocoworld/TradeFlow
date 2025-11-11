package com.github.lye.data;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.Material;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.StonecuttingRecipe;
import com.github.lye.config.Config;
import com.github.lye.util.TradeFlowLogger;
import com.github.lye.util.Format;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CraftingDependencyResolver {

    public enum RecipeType {
        SMELT, COMPRESS, DECOMPRESS, SHAPED, SHAPELESS, STONECUTTER, OTHER
    }

    public static class RecipeData {
        public final List<String> ingredients;
        public final int yield;
        public final RecipeType type;

        public RecipeData(List<String> ingredients, int yield, RecipeType type) {
            this.ingredients = ingredients;
            this.yield = yield;
            this.type = type;
        }
    }

    private final Map<String, List<RecipeData>> dependencyGraph = new HashMap<>();
    private final TradeFlowLogger logger = Format.getLog();
    private final java.util.Set<String> craftForwardWhitelist = new java.util.HashSet<>();
    private final Map<String, String> compressionInverseMap = new HashMap<>();
    private final Map<String, List<String>> inverseDependencyGraph = new HashMap<>();
    private final java.util.Set<String> stonecutterWhitelist = new java.util.HashSet<>();
    private final Database database;

    public CraftingDependencyResolver(Database database) {
        this.database = database;
        // Define common CRAFT_FORWARD compressions
        craftForwardWhitelist.add(Material.IRON_NUGGET.toString().toLowerCase() + "_" + Material.IRON_INGOT.toString().toLowerCase());
        craftForwardWhitelist.add(Material.GOLD_NUGGET.toString().toLowerCase() + "_" + Material.GOLD_INGOT.toString().toLowerCase());
        craftForwardWhitelist.add(Material.IRON_INGOT.toString().toLowerCase() + "_" + Material.IRON_BLOCK.toString().toLowerCase());
        craftForwardWhitelist.add(Material.GOLD_INGOT.toString().toLowerCase() + "_" + Material.GOLD_BLOCK.toString().toLowerCase());
        craftForwardWhitelist.add(Material.DIAMOND.toString().toLowerCase() + "_" + Material.DIAMOND_BLOCK.toString().toLowerCase());
        craftForwardWhitelist.add(Material.EMERALD.toString().toLowerCase() + "_" + Material.EMERALD_BLOCK.toString().toLowerCase());
        craftForwardWhitelist.add(Material.REDSTONE.toString().toLowerCase() + "_" + Material.REDSTONE_BLOCK.toString().toLowerCase());
        craftForwardWhitelist.add(Material.LAPIS_LAZULI.toString().toLowerCase() + "_" + Material.LAPIS_BLOCK.toString().toLowerCase());
        craftForwardWhitelist.add(Material.COAL.toString().toLowerCase() + "_" + Material.COAL_BLOCK.toString().toLowerCase());
        craftForwardWhitelist.add(Material.WHEAT.toString().toLowerCase() + "_" + Material.HAY_BLOCK.toString().toLowerCase());
        // Add more as needed

        // Define common STONECUTTER recipes that should be allowed
        stonecutterWhitelist.add(Material.COBBLESTONE.toString().toLowerCase() + "_" + Material.STONE_BRICKS.toString().toLowerCase());
        stonecutterWhitelist.add(Material.STONE.toString().toLowerCase() + "_" + Material.STONE_BRICKS.toString().toLowerCase());
        // Add more as needed
    }

    private RecipeType classifyRecipe(Recipe recipe, ItemStack result, List<String> ingredients) {
        if (recipe instanceof FurnaceRecipe) {
            return RecipeType.SMELT;
        } else if (recipe instanceof StonecuttingRecipe) {
            return RecipeType.STONECUTTER;
        } else if (recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe) {
            // Check for compress/decompress patterns
            if (ingredients.size() == 9 && result.getAmount() == 1) {
                // 9 ingredients -> 1 result (e.g., 9 ingots -> 1 block)
                // Check if all 9 ingredients are the same type
                if (ingredients.stream().distinct().count() == 1) {
                    return RecipeType.COMPRESS;
                }
            } else if (ingredients.size() == 1 && result.getAmount() == 9) {
                // 1 ingredient -> 9 results (e.g., 1 block -> 9 ingots)
                return RecipeType.DECOMPRESS;
            }

            if (recipe instanceof ShapedRecipe) {
                return RecipeType.SHAPED;
            } else {
                return RecipeType.SHAPELESS;
            }
        }
        return RecipeType.OTHER;
    }

    public List<RecipeData> getRecipeData(String itemName) {
        return dependencyGraph.get(itemName);
    }

    public void buildDependencyGraph() {
        dependencyGraph.clear();
        compressionInverseMap.clear(); // Clear inverse map as well
        inverseDependencyGraph.clear(); // Clear inverse dependency graph as well
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

            RecipeType recipeType = classifyRecipe(recipe, result, ingredients);

            if (recipeType == RecipeType.DECOMPRESS) {
                logger.fine("Ignoring DECOMPRESS recipe for: " + resultName);
                continue;
            }

            if (recipeType == RecipeType.COMPRESS) {
                if (ingredients.isEmpty()) {
                    logger.fine("Ignoring COMPRESS recipe for: " + resultName + " due to empty ingredients list.");
                    continue;
                }
                String ingredientName = ingredients.get(0); // Assuming single ingredient type for COMPRESS
                String whitelistKey = ingredientName + "_" + resultName;
                if (!craftForwardWhitelist.contains(whitelistKey)) {
                    logger.fine("Ignoring non-whitelisted COMPRESS recipe for: " + resultName + " from " + ingredientName);
                    continue;
                }
                // Populate inverse map for whitelisted COMPRESS recipes
                compressionInverseMap.put(ingredientName, resultName);
            }

            if (recipeType == RecipeType.STONECUTTER) {
                if (ingredients.isEmpty()) {
                    logger.fine("Ignoring STONECUTTER recipe for: " + resultName + " due to empty ingredients list.");
                    continue;
                }
                // Assuming a STONECUTTER recipe has only one ingredient
                String ingredientName = ingredients.get(0);
                String whitelistKey = ingredientName + "_" + resultName;
                if (!stonecutterWhitelist.contains(whitelistKey)) {
                    logger.fine("Ignoring non-whitelisted STONECUTTER recipe for: " + resultName + " from " + ingredientName);
                    continue;
                }
            }

            if (!ingredients.isEmpty()) {
                boolean willCreateCycle = false;
                for (String ingredientName : ingredients) {
                    if (wouldCreateCycle(ingredientName, resultName)) {
                        willCreateCycle = true;
                        logger.warning("Skipping recipe for " + resultName + " due to potential cycle with ingredient " + ingredientName);
                        break;
                    }
                }

                if (!willCreateCycle) {
                    dependencyGraph.computeIfAbsent(resultName, k -> new ArrayList<>()) 
                            .add(new RecipeData(ingredients, result.getAmount(), recipeType));

                    for (String ingredientName : ingredients) {
                        inverseDependencyGraph.computeIfAbsent(ingredientName, k -> new ArrayList<>()).add(resultName);
                    }
                }
            }
        }
        logger.info("Built crafting dependency graph with " + dependencyGraph.size() + " items.");
    }

    public void updateCraftedItemPrices(String changedItem) {
        updateCraftedItemPrices(changedItem, new java.util.HashSet<>());    }

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
                    Shop shop = ShopUtil.getShop(this.database, craftedItem, false);
                    if (shop != null) {
                        shop.setPrice(newPrice);
                        ShopUtil.putShop(this.database, craftedItem, shop);
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
                Shop shop = ShopUtil.getShop(this.database, craftedItem, false);
                if (shop != null) {
                    shop.setPrice(newPrice);
                    ShopUtil.putShop(this.database, craftedItem, shop);
                }
            }
        }
        logger.info("Finished calculating initial crafted item prices.");
    }

    public double calculatePrice(String itemName) {
        return calculatePrice(itemName, new java.util.HashSet<>());
    }

    private double getMinFuelCostPerItem() {
        // This is a simplified approach. In a real scenario, you might want to get this from a config or Bukkit's Fuel API.
        // Common fuels and their smelt duration (in ticks, 20 ticks = 1 second)
        // 1 item takes 200 ticks to smelt in a furnace.
        // Coal: 1600 ticks (8 items)
        // Charcoal: 1600 ticks (8 items)
        // Wood (any type): 300 ticks (1.5 items)
        // Blaze Rod: 2400 ticks (12 items)

        Map<String, Integer> fuelSmeltCount = new HashMap<>();
        fuelSmeltCount.put(Material.COAL.toString().toLowerCase(), 8);
        fuelSmeltCount.put(Material.CHARCOAL.toString().toLowerCase(), 8);
        fuelSmeltCount.put(Material.OAK_LOG.toString().toLowerCase(), 1); // Example for wood, assuming 1 item smelted per log
        fuelSmeltCount.put(Material.BLAZE_ROD.toString().toLowerCase(), 12);

        double minCost = Double.MAX_VALUE;

        for (Map.Entry<String, Integer> entry : fuelSmeltCount.entrySet()) {
            String fuelName = entry.getKey();
            int smeltCount = entry.getValue();

            Shop fuelShop = ShopUtil.getShop(this.database, fuelName, false);
            if (fuelShop != null && fuelShop.getPrice() >= 0) {
                double costPerItem = fuelShop.getPrice() / smeltCount;
                if (costPerItem < minCost) {
                    minCost = costPerItem;
                }
            }
        }

        return minCost == Double.MAX_VALUE ? 0 : minCost; // Return 0 if no fuel prices are available
    }

    private double calculatePrice(String itemName, java.util.Set<String> recursionStack) {
        if (recursionStack.contains(itemName)) {
            logger.warning("Circular dependency detected in crafting recipe for: " + itemName);
            return -1;
        }

        // --- New logic for deriving price from reversible conversions ---
        if (Config.get().isTreatReversibleAsDerived() && compressionInverseMap.containsKey(itemName)) {
            String compressedItemName = compressionInverseMap.get(itemName);
            double compressedPrice = calculatePrice(compressedItemName, recursionStack); // Recursively get price of compressed item

            if (compressedPrice >= 0) {
                // Assuming 9:1 compression/decompression ratio
                double derivedPrice = (compressedPrice / 9.0) * (1.0 - Config.get().getAntiArbitrageFee());
                logger.fine("Derived price for " + itemName + " from " + compressedItemName + ": " + derivedPrice);
                return derivedPrice;
            }
        }
        // --- End new logic ---

        List<RecipeData> recipeDataList = dependencyGraph.get(itemName);
        if (recipeDataList == null) {
            // This is a raw material, its price is not calculated.
            Shop shop = ShopUtil.getShop(this.database, itemName, true);
            return shop != null ? shop.getPrice() : -1; // Return -1 if shop not found
        }

        recursionStack.add(itemName);

        double minPrice = Double.MAX_VALUE;
        double minFuelCost = getMinFuelCostPerItem(); // Calculate once per item being priced

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
                if (recipeData.type == RecipeType.SMELT) {
                    currentRecipePrice += minFuelCost; // Add fuel cost for smelting
                }
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

    // Helper method to detect if adding an edge (ingredient -> result) would create a cycle
    private boolean wouldCreateCycle(String ingredient, String result) {
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.Set<String> recursionStack = new java.util.HashSet<>();
        // Check if 'result' is reachable from 'ingredient' in the inverse graph
        return dfsCheckForReachability(result, ingredient, visited, recursionStack, inverseDependencyGraph);
    }

    // DFS to check if targetNode is reachable from currentNode in the given graph
    private boolean dfsCheckForReachability(String currentNode, String targetNode, java.util.Set<String> visited, java.util.Set<String> recursionStack, Map<String, List<String>> graph) {
        visited.add(currentNode);
        recursionStack.add(currentNode);

        List<String> neighbors = graph.get(currentNode);
        if (neighbors != null) {
            for (String neighbor : neighbors) {
                if (neighbor.equals(targetNode)) {
                    return true; // Target reachable
                }
                if (!visited.contains(neighbor)) {
                    if (dfsCheckForReachability(neighbor, targetNode, visited, recursionStack, graph)) {
                        return true;
                    }
                } else if (recursionStack.contains(neighbor)) {
                    // Cycle detected in the current path, but we are only interested in reachability
                }
            }
        }

        recursionStack.remove(currentNode);
        return false;
    }
}