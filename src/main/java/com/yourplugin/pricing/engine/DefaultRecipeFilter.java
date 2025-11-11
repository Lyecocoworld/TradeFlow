package com.yourplugin.pricing.engine;

import com.yourplugin.pricing.model.ItemId;
import com.yourplugin.pricing.model.Recipe;
import com.yourplugin.pricing.service.AuditService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class DefaultRecipeFilter implements RecipeFilter {

    private static final Logger LOGGER = Logger.getLogger(DefaultRecipeFilter.class.getName());
    private final AuditService auditService;
    private final Set<String> excludedRecipesLog = new HashSet<>();

    public DefaultRecipeFilter(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public List<Recipe> filterRecipes(List<Recipe> allRecipes) {
        List<Recipe> initiallyFiltered = new ArrayList<>();

        // Apply initial filtering rules
        for (Recipe recipe : allRecipes) {
            if (isRecipeExcludedByPolicy(recipe)) {
                excludedRecipesLog.add("Recipe for " + recipe.getOutputItem().getFullId() + " (policy exclusion)");
                auditService.logWarning("Excluded recipe by policy: " + recipe.toString());
            } else {
                initiallyFiltered.add(recipe);
            }
        }

        // Build graph from initially filtered recipes
        Graph graph = Graph.from(initiallyFiltered);

        // Detect cycles using Tarjan's algorithm
        Tarjan tarjan = new Tarjan(graph);
        List<List<ItemId>> sccs = tarjan.findSccs();

        List<Recipe> finalFilteredRecipes = new ArrayList<>(initiallyFiltered);
        int cyclesDetected = 0;

        for (List<ItemId> scc : sccs) {
            if (scc.size() > 1) { // SCC with more than one node indicates a cycle
                cyclesDetected++;
                auditService.logWarning("Detected cycle involving items: " + scc.stream().map(ItemId::getFullId).collect(Collectors.joining(", ")));
                // Enforce policy and strip cycles: For now, remove all recipes that are part of the cycle.
                // A more sophisticated approach would remove specific edges based on policy.
                finalFilteredRecipes.removeIf(recipe -> scc.contains(recipe.getOutputItem()) && recipe.getIngredients().keySet().stream().anyMatch(scc::contains));
                excludedRecipesLog.add("Recipes in cycle involving: " + scc.stream().map(ItemId::getFullId).collect(Collectors.joining(", ")));
            }
        }

        if (cyclesDetected > 0) {
            auditService.logInfo(String.format("Built graph: %d items, %d reversible cycles after normalization (filtered %d).",
                    graph.getNodes().size(), cyclesDetected, excludedRecipesLog.size()));
        } else {
            auditService.logInfo(String.format("Built graph: %d items, 0 reversible cycles after normalization (filtered %d).",
                    graph.getNodes().size(), excludedRecipesLog.size()));
        }

        return finalFilteredRecipes;
    }

    private boolean isRecipeExcludedByPolicy(Recipe recipe) {
        // Rule 1: Metal units (nugget/ingot/block) -> one-way decomposition (large -> small)
        // If output is a nugget, check if an ingot/block version is an ingredient
        if (recipe.getOutputItem().getKey().endsWith("_nugget")) {
            boolean createsCycle = recipe.getIngredients().keySet().stream()
                    .anyMatch(ingredient -> (
                            (ingredient.getKey().endsWith("_ingot") || ingredient.getKey().endsWith("_block")) &&
                            ingredient.getNamespace().equals(recipe.getOutputItem().getNamespace()) &&
                            ingredient.getKey().startsWith(recipe.getOutputItem().getKey().replace("_nugget", ""))
                    ));
            if (createsCycle) return true;
        }

        // Rule 2: Colors (wool/bed/carpet/harness) -> interdire “couleur → couleur”
        // This is a simplified check. A more robust solution would involve a list of colorable items.
        if (recipe.getOutputItem().getKey().contains("wool") || recipe.getOutputItem().getKey().contains("bed") ||
            recipe.getOutputItem().getKey().contains("carpet") || recipe.getOutputItem().getKey().contains("harness")) {
            boolean colorToColor = recipe.getIngredients().keySet().stream()
                    .anyMatch(ingredient -> ingredient.getKey().contains("wool") || ingredient.getKey().contains("bed") ||
                                           ingredient.getKey().contains("carpet") || ingredient.getKey().contains("harness"));
            if (colorToColor) return true;
        }

        // Rule 3: Quartz (block/chiseled/slab) -> un seul sens (block -> slab)
        if (recipe.getOutputItem().getKey().contains("quartz")) {
            // Example: If output is quartz block, and ingredient is quartz slab, exclude.
            if (recipe.getOutputItem().getKey().equals("quartz_block")) {
                boolean slabToBlock = recipe.getIngredients().keySet().stream()
                        .anyMatch(ingredient -> ingredient.getKey().equals("quartz_slab"));
                if (slabToBlock) return true;
            }
        }

        // Rule 4: Recyclage outils -> nuggets -> désactivé par défaut.
        // This would require identifying tool recipes and their decomposition into nuggets.
        // For now, a simple placeholder.
        if (recipe.getOutputItem().getKey().endsWith("_nugget")) {
            boolean toolRecycling = recipe.getIngredients().keySet().stream()
                    .anyMatch(ingredient -> ingredient.getKey().contains("_pickaxe") || ingredient.getKey().contains("_sword") ||
                                           ingredient.getKey().contains("_shovel") || ingredient.getKey().contains("_axe") ||
                                           ingredient.getKey().contains("_hoe"));
            if (toolRecycling) return true;
        }

        return false;
    }

    @Override
    public Set<String> getExcludedRecipesLog() {
        return excludedRecipesLog;
    }
}