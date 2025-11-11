package com.yourplugin.pricing.engine;

import com.yourplugin.pricing.model.ItemId;
import com.yourplugin.pricing.model.Recipe;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents the dependency graph of items based on recipes.
 * Nodes are ItemIds, and edges represent dependencies (ingredients needed for an output).
 */
public class Graph {
    private final Map<ItemId, Set<ItemId>> adj;
    private final Map<ItemId, Set<ItemId>> revAdj;
    private final Map<ItemId, List<Recipe>> recipesByOutput;

    private Graph(Map<ItemId, Set<ItemId>> adj, Map<ItemId, Set<ItemId>> revAdj, Map<ItemId, List<Recipe>> recipesByOutput) {
        this.adj = Collections.unmodifiableMap(adj);
        this.revAdj = Collections.unmodifiableMap(revAdj);
        this.recipesByOutput = Collections.unmodifiableMap(recipesByOutput);
    }

    public static Graph from(List<Recipe> recipes) {
        Map<ItemId, Set<ItemId>> adj = new HashMap<>();
        Map<ItemId, Set<ItemId>> revAdj = new HashMap<>();
        Map<ItemId, List<Recipe>> recipesByOutput = new HashMap<>();

        // Initialize all items that are either inputs or outputs
        Set<ItemId> allItems = new HashSet<>();
        for (Recipe recipe : recipes) {
            allItems.add(recipe.getOutputItem());
            allItems.addAll(recipe.getIngredients().keySet());
        }
        for (ItemId item : allItems) {
            adj.put(item, new HashSet<>());
            revAdj.put(item, new HashSet<>());
            recipesByOutput.put(item, new ArrayList<>());
        }

        for (Recipe recipe : recipes) {
            ItemId output = recipe.getOutputItem();
            recipesByOutput.get(output).add(recipe);

            for (ItemId ingredient : recipe.getIngredients().keySet()) {
                // Add edge: ingredient -> output
                adj.get(ingredient).add(output);
                revAdj.get(output).add(ingredient);
            }
        }
        return new Graph(adj, revAdj, recipesByOutput);
    }

    public Set<ItemId> getNodes() {
        return adj.keySet();
    }

    public Set<ItemId> getDependencies(ItemId item) {
        return adj.getOrDefault(item, Collections.emptySet());
    }

    public Set<ItemId> getReverseDependencies(ItemId item) {
        return revAdj.getOrDefault(item, Collections.emptySet());
    }

    public List<Recipe> getRecipesByOutput(ItemId item) {
        return recipesByOutput.getOrDefault(item, Collections.emptyList());
    }

    /**
     * Creates a new Graph instance with a subset of recipes.
     * Useful after filtering or cycle removal.
     * @param newRecipes The list of recipes for the new graph.
     * @return A new Graph instance.
     */
    public Graph withRecipes(List<Recipe> newRecipes) {
        return Graph.from(newRecipes);
    }

    @Override
    public String toString() {
        return "Graph{" +
               "nodes=" + adj.size() +
               ", edges=" + adj.values().stream().mapToInt(Set::size).sum() +
               '}';
    }
}
