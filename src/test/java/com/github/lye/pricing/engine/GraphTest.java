package com.github.lye.pricing.engine;

import com.github.lye.pricing.model.ItemId;
import com.github.lye.pricing.model.QItem;
import com.github.lye.pricing.model.Recipe;
import com.github.lye.pricing.model.RecipeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link Graph}.
 * Verifie la construction a partir de recipes et les accesseeurs.
 */
class GraphTest {

    private static final ItemId IRON = new ItemId("minecraft", "iron_ingot");
    private static final ItemId COAL = new ItemId("minecraft", "coal");
    private static final ItemId IRON_PICKAXE = new ItemId("minecraft", "iron_pickaxe");
    private static final ItemId STICK = new ItemId("minecraft", "stick");

    private Recipe makeRecipe(ItemId output, ItemId... inputs) {
        List<QItem> inputList = Arrays.stream(inputs)
                .map(i -> new QItem(i, 1.0))
                .toList();
        return Recipe.builder().type(RecipeType.CRAFT).output(output).outQty(1.0).inputs(inputList).build();
    }

    // ════════════════════════════════════════════════════════════════
    //  Construction
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Construction a partir de recipes")
    class Construction {

        @Test
        @DisplayName("Graph vide avec liste de recipes vide")
        void emptyGraph() {
            Graph graph = Graph.from(Collections.emptyList());
            assertTrue(graph.getNodes().isEmpty());
        }

        @Test
        @DisplayName("Graph avec une seule recipe contient les bons noeuds")
        void singleRecipe() {
            Recipe recipe = makeRecipe(IRON_PICKAXE, IRON, STICK);
            Graph graph = Graph.from(List.of(recipe));

            Set<ItemId> nodes = graph.getNodes();
            assertEquals(3, nodes.size());
            assertTrue(nodes.contains(IRON_PICKAXE));
            assertTrue(nodes.contains(IRON));
            assertTrue(nodes.contains(STICK));
        }

        @Test
        @DisplayName("Graph avec deux recipes partageant un ingredient")
        void sharedIngredient() {
            Recipe r1 = makeRecipe(IRON_PICKAXE, IRON, STICK);
            Recipe r2 = makeRecipe(new ItemId("minecraft", "iron_axe"), IRON, STICK);
            Graph graph = Graph.from(Arrays.asList(r1, r2));

            Set<ItemId> nodes = graph.getNodes();
            assertTrue(nodes.contains(IRON));
            assertEquals(4, nodes.size()); // iron_pickaxe, iron_axe, iron, stick
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Dependencies
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Dependencies et reverse dependencies")
    class Dependencies {

        @Test
        @DisplayName("getDependencies: ingredient -> outputs")
        void forwardDependencies() {
            Recipe recipe = makeRecipe(IRON_PICKAXE, IRON, STICK);
            Graph graph = Graph.from(List.of(recipe));

            Set<ItemId> deps = graph.getDependencies(IRON);
            assertEquals(1, deps.size());
            assertTrue(deps.contains(IRON_PICKAXE));
        }

        @Test
        @DisplayName("getReverseDependencies: output -> ingredients")
        void reverseDependencies() {
            Recipe recipe = makeRecipe(IRON_PICKAXE, IRON, STICK);
            Graph graph = Graph.from(List.of(recipe));

            Set<ItemId> revDeps = graph.getReverseDependencies(IRON_PICKAXE);
            assertEquals(2, revDeps.size());
            assertTrue(revDeps.contains(IRON));
            assertTrue(revDeps.contains(STICK));
        }

        @Test
        @DisplayName("Noeud sans dependances retourne ensemble vide")
        void noDependencies() {
            Recipe recipe = makeRecipe(IRON_PICKAXE, IRON);
            Graph graph = Graph.from(List.of(recipe));

            // IRON_PICKAXE n'est ingredient d'aucune recipe
            assertTrue(graph.getDependencies(IRON_PICKAXE).isEmpty());
        }

        @Test
        @DisplayName("Item absent du graphe retourne ensemble vide")
        void missingItemReturnsEmpty() {
            Graph graph = Graph.from(Collections.emptyList());
            ItemId unknown = new ItemId("minecraft", "unknown");
            assertTrue(graph.getDependencies(unknown).isEmpty());
            assertTrue(graph.getReverseDependencies(unknown).isEmpty());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Recipes par output
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getRecipesByOutput")
    class RecipesByOutput {

        @Test
        @DisplayName("Recupere les recipes pour un output donne")
        void recipesForOutput() {
            Recipe r1 = makeRecipe(IRON_PICKAXE, IRON, STICK);
            Recipe r2 = makeRecipe(IRON_PICKAXE, COAL); // Alternative recipe
            Graph graph = Graph.from(Arrays.asList(r1, r2));

            List<Recipe> recipes = graph.getRecipesByOutput(IRON_PICKAXE);
            assertEquals(2, recipes.size());
        }

        @Test
        @DisplayName("Item sans recipe retourne liste vide")
        void noRecipesForItem() {
            Graph graph = Graph.from(List.of(makeRecipe(IRON_PICKAXE, IRON)));
            assertTrue(graph.getRecipesByOutput(IRON).isEmpty());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Immutabilite
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Immutabilite du Graph")
    class Immutability {

        @Test
        @DisplayName("Modifier la liste de recipes originale n'affecte pas le graphe")
        void recipeListImmutable() {
            java.util.List<Recipe> recipes = new java.util.ArrayList<>();
            recipes.add(makeRecipe(IRON_PICKAXE, IRON));

            Graph graph = Graph.from(recipes);
            recipes.add(makeRecipe(new ItemId("minecraft", "iron_axe"), IRON));

            // Le graphe ne devrait contenir que les noeuds de la premiere recipe
            assertEquals(2, graph.getNodes().size());
        }

        @Test
        @DisplayName("withRecipes cree un nouveau graphe")
        void withRecipesCreatesNewGraph() {
            Recipe r1 = makeRecipe(IRON_PICKAXE, IRON);
            Graph original = Graph.from(List.of(r1));

            Recipe r2 = makeRecipe(new ItemId("minecraft", "iron_axe"), IRON);
            Graph modified = original.withRecipes(Arrays.asList(r1, r2));

            assertNotSame(original, modified);
            assertEquals(2, original.getNodes().size());
            assertEquals(3, modified.getNodes().size());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  toString
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("toString affiche le nombre de noeuds et d'aretes")
    void toString_showsStats() {
        Graph graph = Graph.from(List.of(makeRecipe(IRON_PICKAXE, IRON, STICK)));
        String str = graph.toString();
        assertTrue(str.contains("nodes=3"));
    }
}
