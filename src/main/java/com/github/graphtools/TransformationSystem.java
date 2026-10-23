package com.github.graphtools;


import lombok.*;

import java.util.List;
import java.util.Map;

@Data
public class TransformationSystem {
    private final Graph graph;
    private final Grammar grammar;

    @Setter(AccessLevel.NONE)
    private History history;

    public TransformationSystem apply(List<String> productions, String selectionStrategy, String danglingEdges) {
        String previousDanglingEdges = grammar.getDanglingEdges();
        Graph previousStartGraph = grammar.getStartGraph();
        grammar.setDanglingEdges(danglingEdges);

        List<Map<Integer, Integer>> allLeftToGraph; //todo finish getting mappings



        if (selectionStrategy.equals("single")) {
            Map<Integer, Integer> leftToGraph = a;
        }


        grammar.setDanglingEdges(previousDanglingEdges);
        grammar.setStartGraph(previousStartGraph);
        return this;
    }

    private void initializeHistoryIfAbsent() {
        if (history == null) {
            history = new History();
        }
    }
}
