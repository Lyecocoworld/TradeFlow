package com.yourplugin.pricing.engine;

import com.yourplugin.pricing.model.Recipe;

import java.util.List;
import java.util.Set;

/**
 * Interface for filtering and normalizing recipes to prevent pricing cycles and inconsistencies.
 */
public interface RecipeFilter {

    /**
     * Filters a list of recipes, removing those that create cycles or are otherwise invalid
     * according to predefined rules (e.g., metal decomposition, colored item conversions).
     *
     * @param allRecipes The complete list of recipes to filter.
     * @return A filtered list of valid recipes.
     */
    List<Recipe> filterRecipes(List<Recipe> allRecipes);

    /**
     * Returns a set of ItemIds that were excluded by the filter, along with a reason.
     * This is useful for auditing and logging.
     * @return A map of ItemId to exclusion reason.
     */
    Set<String> getExcludedRecipesLog();
}
