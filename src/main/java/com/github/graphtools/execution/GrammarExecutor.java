package com.github.graphtools.execution;
import com.github.graphtools.grammar.Grammar;
import com.github.graphtools.grammar.Production;

import com.github.graphtools.model.GraphStructure;

import java.util.*;

public class GrammarExecutor {
    private final ProductionApplier productionApplier = new ProductionApplier();
    private GraphStructure graph;
    private final Grammar grammar;
    private final ExecutionMode executionMode;
    private final String danglingEdges;
    private final List<String> productions;
    private int position;

    public GrammarExecutor(Grammar grammar, List<String> productions) {
        this(grammar.getStartGraph(), grammar, productions, null);
    }

    public GrammarExecutor(
            Grammar grammar, List<String> productions, String danglingEdges) {
        this(grammar.getStartGraph(), grammar, productions, danglingEdges);
    }

    public GrammarExecutor(
            GraphStructure graph, Grammar grammar, List<String> productions) {
        this(graph, grammar, productions, null);
    }

    public GrammarExecutor(
            GraphStructure graph, Grammar grammar,
            List<String> productions, String danglingEdges) {
        this(graph, grammar, productions, danglingEdges,
                ExecutionMode.EMBEDDING_PRIORITY);
    }

    public GrammarExecutor(
            GraphStructure graph, Grammar grammar,
            List<String> productions, String danglingEdges,
            ExecutionMode executionMode) {
        this.executionMode = Objects.requireNonNull(
                executionMode, "executionMode must not be null");
        this.grammar = Objects.requireNonNull(grammar, "grammar must not be null");
        this.graph = new GraphStructure(
                Objects.requireNonNull(graph, "graph must not be null"));
        this.graph.validate();
        if (this.graph.getDirected() == null) {
            this.graph.setDirected(grammar.isDirected());
        }
        if (this.graph.isDirected() != grammar.isDirected()) {
            throw new IllegalArgumentException(
                    "Graph directedness does not match the grammar");
        }
        if (danglingEdges != null) {
            Grammar.shouldForbidDanglingEdges(danglingEdges);
        }
        this.danglingEdges = danglingEdges;
        this.productions = List.copyOf(productions);
        for (String name : this.productions) {
            findProduction(name);
        }
    }

    public GraphStructure getGraph() {
        return new GraphStructure(graph);
    }

    public boolean hasNext() {
        return position < productions.size();
    }

    public GraphStructure next() {
        return next(false);
    }

    public GraphStructure next(boolean many) {
        return next(many, false);
    }

    GraphStructure next(boolean many, boolean ascending) {
        if (!hasNext()) {
            throw new NoSuchElementException("No more productions");
        }

        Production production = executionMode.prepare(
                findProduction(productions.get(position)));
        String policy = danglingEdges != null
                ? danglingEdges
                : production.getDanglingEdges() != null
                    ? production.getDanglingEdges()
                    : grammar.getDanglingEdges();
        boolean forbid = Grammar.shouldForbidDanglingEdges(policy);

        GraphStructure candidate = new GraphStructure(graph);
        List<Map<Integer, Integer>> matches = new ArrayList<>(
                candidate.findSubGraphMapping(production.getLeft(), true));
        matches.sort((first, second) ->
                compareMappings(first, second, ascending));

        Set<Integer> usedVertices = new HashSet<>();
        for (Map<Integer, Integer> match : matches) {
            if (!Collections.disjoint(usedVertices, match.values())) {
                continue;
            }
            // Earlier replacements can also change edges between matches.
            if (!candidate.findSubGraphMapping(production.getLeft(), true)
                    .contains(match)) {
                continue;
            }
            productionApplier.applyProduction(candidate, production, match, forbid);
            usedVertices.addAll(match.values());
            if (!many) {
                break;
            }
        }

        graph = candidate;
        position++;
        return getGraph();
    }

    private Production findProduction(String name) {
        return grammar.getProductions().stream()
                .filter(production -> Objects.equals(production.getName(), name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Production does not exist: " + name));
    }

    private static int compareMappings(
            Map<Integer, Integer> first, Map<Integer, Integer> second,
            boolean ascending) {
        Comparator<Integer> order = ascending
                ? Comparator.naturalOrder()
                : Comparator.reverseOrder();
        List<Integer> firstIndices = first.values().stream()
                .sorted(order).toList();
        List<Integer> secondIndices = second.values().stream()
                .sorted(order).toList();
        for (int i = 0; i < firstIndices.size(); i++) {
            int comparison = order.compare(
                    firstIndices.get(i), secondIndices.get(i));
            if (comparison != 0) {
                return comparison;
            }
        }
        // Resolve symmetric mappings consistently by pattern vertex index.
        for (Integer index : first.keySet().stream().sorted().toList()) {
            int comparison = order.compare(first.get(index), second.get(index));
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }
}
