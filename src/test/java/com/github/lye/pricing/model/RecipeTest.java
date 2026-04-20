package com.github.lye.pricing.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link Recipe}.
 * Verifie la construction via Builder, les invariants et les accesseeurs.
 */
class RecipeTest {

    private static final ItemId IRON = new ItemId("minecraft", "iron_ingot");
    private static final ItemId COAL = new ItemId("minecraft", "coal");
    private static final ItemId IRON_PICKAXE = new ItemId("minecraft", "iron_pickaxe");
    private static final ItemId STICK = new ItemId("minecraft", "stick");

    // ════════════════════════════════════════════════════════════════
    //  Construction via Builder
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Construction via Builder")
    class BuilderConstruction {

        @Test
        @DisplayName("Recipe minimal valide")
        void minimalRecipe() {
            Recipe recipe = Recipe.builder()
                    .type(RecipeType.CRAFT)
                    .output(IRON_PICKAXE)
                    .outQty(1.0)
                    .build();

            assertEquals(RecipeType.CRAFT, recipe.getType());
            assertEquals(IRON_PICKAXE, recipe.getOutputItem());
            assertEquals(1.0, recipe.getOutputQuantity());
            assertTrue(recipe.getInputs().isEmpty());
            assertTrue(recipe.getByproducts().isEmpty());
            assertEquals(0.0, recipe.getFuelCost());
            assertEquals(0.0, recipe.getSeconds());
        }

        @Test
        @DisplayName("Recipe complete avec inputs")
        void fullRecipe() {
            List<QItem> inputs = Arrays.asList(
                    new QItem(IRON, 3.0),
                    new QItem(STICK, 2.0)
            );

            Recipe recipe = Recipe.builder()
                    .type(RecipeType.CRAFT)
                    .output(IRON_PICKAXE)
                    .outQty(1.0)
                    .inputs(inputs)
                    .fuelCost(5.0)
                    .seconds(3.0)
                    .build();

            assertEquals(2, recipe.getInputs().size());
            assertEquals(5.0, recipe.getFuelCost());
            assertEquals(3.0, recipe.getSeconds());
        }

        @Test
        @DisplayName("Quantite de sortie 0 leve IllegalArgumentException")
        void zeroOutputQuantity() {
            assertThrows(IllegalArgumentException.class, () ->
                    Recipe.builder()
                            .type(RecipeType.CRAFT)
                            .output(IRON_PICKAXE)
                            .outQty(0.0)
                            .build()
            );
        }

        @Test
        @DisplayName("Quantite de sortie negative leve IllegalArgumentException")
        void negativeOutputQuantity() {
            assertThrows(IllegalArgumentException.class, () ->
                    Recipe.builder()
                            .type(RecipeType.CRAFT)
                            .output(IRON_PICKAXE)
                            .outQty(-1.0)
                            .build()
            );
        }

        @Test
        @DisplayName("Type null leve NullPointerException")
        void nullType() {
            assertThrows(NullPointerException.class, () ->
                    Recipe.builder()
                            .type(null)
                            .output(IRON_PICKAXE)
                            .outQty(1.0)
                            .build()
            );
        }

        @Test
        @DisplayName("Output null leve NullPointerException")
        void nullOutput() {
            assertThrows(NullPointerException.class, () ->
                    Recipe.builder()
                            .type(RecipeType.CRAFT)
                            .output(null)
                            .outQty(1.0)
                            .build()
            );
        }

        @Test
        @DisplayName("Quantite de sortie fractionnaire valide")
        void fractionalOutputQuantity() {
            Recipe recipe = Recipe.builder()
                    .type(RecipeType.SMELT)
                    .output(IRON)
                    .outQty(0.5)
                    .build();
            assertEquals(0.5, recipe.getOutputQuantity());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  getIngredients
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getIngredients: conversion en Map")
    class Ingredients {

        @Test
        @DisplayName("getIngredients retourne une Map ItemId -> quantite")
        void ingredientsMap() {
            List<QItem> inputs = Arrays.asList(
                    new QItem(IRON, 3.0),
                    new QItem(STICK, 2.0)
            );

            Recipe recipe = Recipe.builder()
                    .type(RecipeType.CRAFT)
                    .output(IRON_PICKAXE)
                    .outQty(1.0)
                    .inputs(inputs)
                    .build();

            Map<ItemId, Double> ingredients = recipe.getIngredients();
            assertEquals(2, ingredients.size());
            assertEquals(3.0, ingredients.get(IRON));
            assertEquals(2.0, ingredients.get(STICK));
        }

        @Test
        @DisplayName("getIngredients avec liste vide retourne Map vide")
        void emptyIngredients() {
            Recipe recipe = Recipe.builder()
                    .type(RecipeType.CRAFT)
                    .output(IRON_PICKAXE)
                    .outQty(1.0)
                    .build();

            assertTrue(recipe.getIngredients().isEmpty());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Immutabilite
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Immutabilite")
    class Immutability {

        @Test
        @DisplayName("La liste des inputs est non-modifiable via getInputs()")
        void inputsImmutableViaAccessor() {
            // Note: Collections.unmodifiableList est une VUE, pas une deep copy.
            // Le contrat est que getInputs() retourne une liste non-modifiable.
            Recipe recipe = Recipe.builder()
                    .type(RecipeType.CRAFT)
                    .output(IRON_PICKAXE)
                    .outQty(1.0)
                    .inputs(Collections.singletonList(new QItem(IRON, 1.0)))
                    .build();

            assertThrows(UnsupportedOperationException.class, () ->
                    recipe.getInputs().clear()
            );
            assertEquals(1, recipe.getInputs().size());
        }

        @Test
        @DisplayName("La liste des inputs est non-modifiable")
        void inputsListUnmodifiable() {
            Recipe recipe = Recipe.builder()
                    .type(RecipeType.CRAFT)
                    .output(IRON_PICKAXE)
                    .outQty(1.0)
                    .inputs(Collections.singletonList(new QItem(IRON, 1.0)))
                    .build();

            assertThrows(UnsupportedOperationException.class, () ->
                    recipe.getInputs().add(new QItem(STICK, 1.0))
            );
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Types de recipe
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Types de recipe")
    class RecipeTypes {

        @Test
        @DisplayName("Tous les RecipeType sont utilisables")
        void allRecipeTypes() {
            for (RecipeType type : RecipeType.values()) {
                Recipe recipe = Recipe.builder()
                        .type(type)
                        .output(IRON)
                        .outQty(1.0)
                        .build();
                assertEquals(type, recipe.getType());
            }
        }

        @Test
        @DisplayName("RecipeType enum contient les types attendus")
        void expectedTypes() {
            RecipeType[] types = RecipeType.values();
            assertEquals(7, types.length);
            assertTrue(Arrays.stream(types).anyMatch(t -> t == RecipeType.CRAFT));
            assertTrue(Arrays.stream(types).anyMatch(t -> t == RecipeType.SMELT));
            assertTrue(Arrays.stream(types).anyMatch(t -> t == RecipeType.BLAST));
            assertTrue(Arrays.stream(types).anyMatch(t -> t == RecipeType.SMOKE));
            assertTrue(Arrays.stream(types).anyMatch(t -> t == RecipeType.STONECUT));
            assertTrue(Arrays.stream(types).anyMatch(t -> t == RecipeType.SMITH));
            assertTrue(Arrays.stream(types).anyMatch(t -> t == RecipeType.CUSTOM));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Egalite
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Egalite")
    class Equality {

        @Test
        @DisplayName("Deux recipes identiques sont egales")
        void equalRecipes() {
            List<QItem> inputs = Collections.singletonList(new QItem(IRON, 3.0));
            Recipe a = Recipe.builder().type(RecipeType.CRAFT).output(IRON_PICKAXE).outQty(1.0).inputs(inputs).build();
            Recipe b = Recipe.builder().type(RecipeType.CRAFT).output(IRON_PICKAXE).outQty(1.0).inputs(inputs).build();
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("Recipes avec types differents ne sont pas egales")
        void differentType() {
            Recipe a = Recipe.builder().type(RecipeType.CRAFT).output(IRON_PICKAXE).outQty(1.0).build();
            Recipe b = Recipe.builder().type(RecipeType.SMELT).output(IRON_PICKAXE).outQty(1.0).build();
            assertNotEquals(a, b);
        }
    }
}
