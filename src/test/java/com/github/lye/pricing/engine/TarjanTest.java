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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link Tarjan}.
 * Verifie la detection de cycles dans le graphe de dependances.
 */
class TarjanTest {

    private static final ItemId A = new ItemId("minecraft", "a");
    private static final ItemId B = new ItemId("minecraft", "b");
    private static final ItemId C = new ItemId("minecraft", "c");
    private static final ItemId D = new ItemId("minecraft", "d");

    private Recipe makeRecipe(ItemId output, ItemId... inputs) {
        List<QItem> inputList = Arrays.stream(inputs)
                .map(i -> new QItem(i, 1.0))
                .toList();
        return Recipe.builder().type(RecipeType.CUSTOM).output(output).outQty(1.0).inputs(inputList).build();
    }

    // ════════════════════════════════════════════════════════════════
    //  Cas sans cycle
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Graphe sans cycle (DAG)")
    class NoCycles {

        @Test
        @DisplayName("Graphe vide: aucun SCC")
        void emptyGraph() {
            Graph graph = Graph.from(Collections.emptyList());
            Tarjan tarjan = new Tarjan(graph);
            List<List<ItemId>> sccs = tarjan.findSccs();
            assertTrue(sccs.isEmpty());
        }

        @Test
        @DisplayName("Chain lineaire A -> B -> C: pas de cycle")
        void linearChain() {
            Recipe r1 = makeRecipe(B, A);
            Recipe r2 = makeRecipe(C, B);
            Graph graph = Graph.from(Arrays.asList(r1, r2));

            Tarjan tarjan = new Tarjan(graph);
            List<List<ItemId>> sccs = tarjan.findSccs();

            // Tous les SCCs devraient etre de taille 1 (pas de cycle)
            for (List<ItemId> scc : sccs) {
                assertEquals(1, scc.size(), "Pas de cycle attendu, mais SCC de taille > 1 trouve");
            }
        }

        @Test
        @DisplayName("Noeud isole: SCC de taille 1")
        void isolatedNode() {
            Recipe r = makeRecipe(A); // Pas d'ingredients
            Graph graph = Graph.from(List.of(r));

            Tarjan tarjan = new Tarjan(graph);
            List<List<ItemId>> sccs = tarjan.findSccs();

            assertEquals(1, sccs.size());
            assertEquals(1, sccs.get(0).size());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Cycles
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Detection de cycles")
    class CycleDetection {

        @Test
        @DisplayName("Cycle simple A -> B -> A")
        void simpleCycle() {
            Recipe r1 = makeRecipe(B, A); // A est ingredient de B
            Recipe r2 = makeRecipe(A, B); // B est ingredient de A → cycle
            Graph graph = Graph.from(Arrays.asList(r1, r2));

            Tarjan tarjan = new Tarjan(graph);
            List<List<ItemId>> sccs = tarjan.findSccs();

            // Il devrait y avoir exactement 1 SCC de taille 2
            boolean foundCycle = sccs.stream().anyMatch(scc -> scc.size() > 1);
            assertTrue(foundCycle, "Un cycle A ↔ B devrait etre detecte");

            List<ItemId> cycleScc = sccs.stream()
                    .filter(scc -> scc.size() > 1)
                    .findFirst()
                    .orElse(Collections.emptyList());
            assertEquals(2, cycleScc.size());
            assertTrue(cycleScc.contains(A));
            assertTrue(cycleScc.contains(B));
        }

        @Test
        @DisplayName("Cycle de 3 elements A -> B -> C -> A")
        void threeNodeCycle() {
            Recipe r1 = makeRecipe(B, A);
            Recipe r2 = makeRecipe(C, B);
            Recipe r3 = makeRecipe(A, C);
            Graph graph = Graph.from(Arrays.asList(r1, r2, r3));

            Tarjan tarjan = new Tarjan(graph);
            List<List<ItemId>> sccs = tarjan.findSccs();

            boolean foundCycle = sccs.stream().anyMatch(scc -> scc.size() > 1);
            assertTrue(foundCycle, "Un cycle A → B → C → A devrait etre detecte");
        }

        @Test
        @DisplayName("Cycle + noeud isole: un SCC cyclique et un SCC seul")
        void cycleWithIsolatedNode() {
            Recipe r1 = makeRecipe(B, A);
            Recipe r2 = makeRecipe(A, B); // cycle A ↔ B
            Recipe r3 = makeRecipe(D);      // D isole
            Graph graph = Graph.from(Arrays.asList(r1, r2, r3));

            Tarjan tarjan = new Tarjan(graph);
            List<List<ItemId>> sccs = tarjan.findSccs();

            long cycleCount = sccs.stream().filter(scc -> scc.size() > 1).count();
            assertEquals(1, cycleCount, "Exactement 1 cycle attendu");
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Proprietes
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Proprietes des SCCs")
    class SccProperties {

        @Test
        @DisplayName("Chaque noeud apparait exactement dans un SCC")
        void eachNodeInExactlyOneScc() {
            Recipe r1 = makeRecipe(B, A);
            Recipe r2 = makeRecipe(C, B);
            Recipe r3 = makeRecipe(D, C, A);
            Graph graph = Graph.from(Arrays.asList(r1, r2, r3));

            Tarjan tarjan = new Tarjan(graph);
            List<List<ItemId>> sccs = tarjan.findSccs();

            long totalNodes = sccs.stream().mapToLong(List::size).sum();
            assertEquals(graph.getNodes().size(), totalNodes);
        }

        @Test
        @DisplayName("Le nombre total de SCCs couvre tous les noeuds")
        void allNodesCovered() {
            Recipe r1 = makeRecipe(B, A);
            Recipe r2 = makeRecipe(A, B); // cycle
            Graph graph = Graph.from(Arrays.asList(r1, r2));

            Tarjan tarjan = new Tarjan(graph);
            List<List<ItemId>> sccs = tarjan.findSccs();

            // Tous les noeuds du graphe doivent etre dans un SCC
            for (ItemId node : graph.getNodes()) {
                boolean found = sccs.stream().anyMatch(scc -> scc.contains(node));
                assertTrue(found, "Noeud " + node + " devrait etre dans un SCC");
            }
        }
    }
}
