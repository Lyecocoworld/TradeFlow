package com.yourplugin.pricing.engine;

import com.yourplugin.pricing.model.Recipe;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;

/**
 * Factory for loading and creating Recipe objects from various sources (vanilla, custom config).
 */
public interface RecipeFactory {

    /**
     * Loads all vanilla recipes available on the server.
     * @param server The Bukkit Server instance to retrieve vanilla recipes from.
     * @return A list of Recipe objects representing vanilla recipes.
     */
    List<Recipe> loadVanillaRecipes(Server server);

    /**
     * Loads custom recipes from a given YAML configuration section.
     * Custom recipes are expected in a compact string format (e.g., "a*2 + b*1 -> out*3").
     * @param configSection The ConfigurationSection containing custom recipe definitions.
     * @return A list of Recipe objects representing custom recipes.
     */
    List<Recipe> loadCustomRecipes(YamlConfiguration configSection);
}
