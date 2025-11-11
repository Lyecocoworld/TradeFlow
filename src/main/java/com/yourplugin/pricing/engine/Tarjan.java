package com.yourplugin.pricing.engine;

import com.yourplugin.pricing.model.ItemId;

import java.util.*;

/**
 * Implements Tarjan's algorithm to find Strongly Connected Components (SCCs) in a directed graph.
 * Used for cycle detection in the recipe dependency graph.
 */
public class Tarjan {

    private final Graph graph;
    private int time;
    private final Map<ItemId, Integer> discoveryTime;
    private final Map<ItemId, Integer> lowLink;
    private final Stack<ItemId> stack;
    private final Map<ItemId, Boolean> onStack;
    private final List<List<ItemId>> sccs;

    public Tarjan(Graph graph) {
        this.graph = graph;
        this.time = 0;
        this.discoveryTime = new HashMap<>();
        this.lowLink = new HashMap<>();
        this.stack = new Stack<>();
        this.onStack = new HashMap<>();
        this.sccs = new ArrayList<>();

        for (ItemId node : graph.getNodes()) {
            discoveryTime.put(node, -1);
            lowLink.put(node, -1);
            onStack.put(node, false);
        }
    }

    public List<List<ItemId>> findSccs() {
        for (ItemId node : graph.getNodes()) {
            if (discoveryTime.get(node) == -1) {
                dfs(node);
            }
        }
        return sccs;
    }

    private void dfs(ItemId u) {
        discoveryTime.put(u, time);
        lowLink.put(u, time);
        time++;
        stack.push(u);
        onStack.put(u, true);

        for (ItemId v : graph.getDependencies(u)) {
            if (discoveryTime.get(v) == -1) {
                dfs(v);
                lowLink.put(u, Math.min(lowLink.get(u), lowLink.get(v)));
            } else if (onStack.get(v)) {
                lowLink.put(u, Math.min(lowLink.get(u), discoveryTime.get(v)));
            }
        }

        if (lowLink.get(u).equals(discoveryTime.get(u))) {
            List<ItemId> scc = new ArrayList<>();
            while (true) {
                ItemId node = stack.pop();
                onStack.put(node, false);
                scc.add(node);
                if (node.equals(u)) {
                    break;
                }
            }
            sccs.add(scc);
        }
    }
}
