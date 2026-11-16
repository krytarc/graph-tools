package com.github.graphtools.grammar;
import com.github.graphtools.execution.GrammarExecutor;
import com.github.graphtools.execution.ProductionApplier;

import com.github.graphtools.model.Edge;
import com.github.graphtools.model.Graph;
import com.github.graphtools.model.GraphStructure;
import com.github.graphtools.model.Vertex;
import com.fasterxml.jackson.annotation.JsonRootName;
import lombok.Data;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A graph grammar that applies named productions to its start graph.
 * Dangling context edges are either rejected or deleted according to
 * {@code danglingEdges}.
 */
@Data
@JsonRootName("grammar")
public class Grammar {

    String name = "";
    boolean directed = false;
    String danglingEdges = "forbid";

    Label labels;
    Graph startGraph;
    List<Production> productions;

    /** Parses a grammar from JSON containing a {@code grammar} root. */
    public static Grammar fromJson(String json) {
        try {
            final JsonMapper mapper = JsonMapper
                    .builder()
                    .enable(DeserializationFeature.UNWRAP_ROOT_VALUE)
                    .build();
            Grammar grammar = mapper.readValue(json, Grammar.class);
            grammar.validate();
            return grammar;
        } catch (JacksonException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    /**
     * Validates the grammar, its start graph, and every production after parsing.
     *
     * @throws IllegalArgumentException if any grammar rule or graph is malformed
     */
    public void validate() {
        if (labels == null || labels.getNonterminals() == null || labels.getTerminals() == null) {
            throw new IllegalArgumentException(
                    "Grammar must define nonterminals and terminals");
        }
        if (startGraph == null) {
            throw new IllegalArgumentException("Grammar must define a startGraph");
        }

        Set<String> nonterminals = new HashSet<>(labels.getNonterminals());
        Set<String> terminals = new HashSet<>(labels.getTerminals());
        if (nonterminals.size() != labels.getNonterminals().size()
                || terminals.size() != labels.getTerminals().size()) {
            throw new IllegalArgumentException("Terminal and nonterminal labels must be unique");
        }
        Set<String> overlappingLabels = new HashSet<>(nonterminals);
        overlappingLabels.retainAll(terminals);
        if (!overlappingLabels.isEmpty()) {
            throw new IllegalArgumentException(
                    "Terminal and nonterminal labels must be disjoint: " + overlappingLabels);
        }
        validateDanglingEdgePolicy();

        validateGrammarGraph(startGraph, "startGraph", nonterminals, terminals);
        if (productions == null) {
            throw new IllegalArgumentException("Grammar productions must be provided");
        }

        Set<String> productionNames = new HashSet<>();
        for (Production production : productions) {
            if (production == null) {
                throw new IllegalArgumentException("Grammar must not contain null productions");
            }
            validateProduction(production, nonterminals, terminals);
            if (production.getName() == null || production.getName().isBlank()) {
                throw new IllegalArgumentException("Production name must not be blank");
            }
            if (!productionNames.add(production.getName())) {
                throw new IllegalArgumentException(
                        "Duplicate production name: " + production.getName());
            }
        }
    }

    private void validateProduction(
            Production production, Set<String> nonterminals, Set<String> terminals) {
        String productionName = production.getName();
        if (production.getDanglingEdges() != null) {
            shouldForbidDanglingEdges(production.getDanglingEdges());
        }
        if (production.getLeft() == null || production.getRight() == null) {
            throw new IllegalArgumentException(
                    "Production " + productionName + " must define left and right graphs");
        }
        validateGrammarGraph(
                production.getLeft(), productionName + ".left", nonterminals, terminals);
        validateGrammarGraph(
                production.getRight(), productionName + ".right", nonterminals, terminals);

        boolean hasNonterminal = production.getLeft().getVertices().stream()
                .anyMatch(vertex -> nonterminals.contains(vertex.getLabel()));
        if (!hasNonterminal) {
            throw new IllegalArgumentException(
                    "Production " + productionName
                            + " left graph must contain at least one nonterminal");
        }

        List<Mapping> mappings = production.getMappings();
        if (mappings == null) {
            throw new IllegalArgumentException(
                    "Production " + productionName + " mappings must not be null");
        }
        Set<Integer> mappedLeftIndices = new HashSet<>();
        Set<Integer> mappedRightIndices = new HashSet<>();
        for (Mapping mapping : mappings) {
            if (mapping == null) {
                throw new IllegalArgumentException(
                        "Production " + productionName + " must not contain null mappings");
            }
            requireVertex(production.getLeft(), mapping.left(), "mapped left vertex");
            requireVertex(production.getRight(), mapping.right(), "mapped right vertex");
            if (!mappedLeftIndices.add(mapping.left())) {
                throw new IllegalArgumentException(
                        "Production " + productionName
                                + " maps left vertex more than once: " + mapping.left());
            }
            if (!mappedRightIndices.add(mapping.right())) {
                throw new IllegalArgumentException(
                        "Production " + productionName
                                + " maps right vertex more than once: " + mapping.right());
            }
        }

        List<Embedding> embeddings = production.getEmbeddings();
        if (embeddings == null) {
            throw new IllegalArgumentException(
                    "Production " + productionName + " embeddings must not be null");
        }
        for (Embedding embedding : embeddings) {
            if (embedding == null) {
                throw new IllegalArgumentException(
                        "Production " + productionName + " must not contain null embeddings");
            }
            requireVertex(production.getLeft(), embedding.getLeftVertex(), "embedding left vertex");
            if (embedding.getNeighborLabel() == null
                    || (!"*".equals(embedding.getNeighborLabel())
                    && !nonterminals.contains(embedding.getNeighborLabel())
                    && !terminals.contains(embedding.getNeighborLabel()))) {
                throw new IllegalArgumentException(
                        "Embedding has an unknown neighborLabel in production " + productionName);
            }
            List<Integer> connectTo = embedding.getConnectTo();
            if (connectTo == null) {
                throw new IllegalArgumentException(
                        "Embedding connectTo must not be null in production "
                                + productionName);
            }
            for (Integer rightIndex : connectTo) {
                if (rightIndex == null) {
                    throw new IllegalArgumentException(
                            "Embedding connectTo must not contain null indices in production "
                                    + productionName);
                }
                requireVertex(production.getRight(), rightIndex, "embedding right vertex");
            }
        }
    }

    private void validateGrammarGraph(
            GraphStructure graph, String graphName,
            Set<String> nonterminals, Set<String> terminals) {
        graph.validate();
        if (graph.getDirected() == null) {
            graph.setDirected(directed);
        }
        if (graph.isDirected() != directed) {
            throw new IllegalArgumentException(
                    graphName + " directedness does not match the grammar");
        }

        Set<String> validLabels = new HashSet<>(nonterminals);
        validLabels.addAll(terminals);
        for (Vertex vertex : graph.getVertices()) {
            if (!validLabels.contains(vertex.getLabel())) {
                throw new IllegalArgumentException(
                        graphName + " uses an unknown vertex label: " + vertex.getLabel());
            }
        }
        for (Edge edge : graph.getEdges()) {
            if (edge.getLabel() != null && !edge.getLabel().isEmpty()
                    && !validLabels.contains(edge.getLabel())) {
                throw new IllegalArgumentException(
                        graphName + " uses an unknown edge label: " + edge.getLabel());
            }
        }
    }

    private void validateDanglingEdgePolicy() {
        if (danglingEdges == null
                || (!"forbid".equalsIgnoreCase(danglingEdges)
                && !"delete".equalsIgnoreCase(danglingEdges))) {
            throw new IllegalArgumentException(
                    "danglingEdges must be either 'forbid' or 'delete'");
        }
    }

    /**
     * Applies the named productions in order to a copy of the start graph.
     * A production with no matching left-hand side is skipped.
     *
     * @param productionNames ordered production names to apply
     * @return the resulting graph
     * @throws IllegalArgumentException if a production name is unknown or
     *         {@code danglingEdges} has an unsupported value
     * @throws IllegalStateException if a production violates the dangling-edge policy
     */
    public GraphStructure executeProductions(List<String> productionNames) {
        Objects.requireNonNull(productionNames, "productionNames must not be null");
        GrammarExecutor executor = new GrammarExecutor(this, productionNames);
        while (executor.hasNext()) {
            executor.next();
        }
        return executor.getGraph();
    }

    /** Delegates an already matched production to the transformation engine. */
    public void applyProduction(
            GraphStructure graph, Production production,
            Map<Integer, Integer> leftToGraph, boolean forbidDanglingEdges) {
        new ProductionApplier().applyProduction(
                graph, production, leftToGraph, forbidDanglingEdges);
    }

    public static boolean shouldForbidDanglingEdges(String danglingEdges) {
        if (danglingEdges == null) {
            throw new IllegalArgumentException(
                    "danglingEdges must be either 'forbid' or 'delete'");
        }
        return switch (danglingEdges.toLowerCase(Locale.ROOT)) {
            case "forbid" -> true;
            case "delete" -> false;
            default -> throw new IllegalArgumentException(
                    "danglingEdges must be either 'forbid' or 'delete'");
        };
    }

    private static Vertex requireVertex(GraphStructure graph, int index, String description) {
        return graph.getVertices().stream()
                .filter(vertex -> vertex.getIndex() == index)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Missing " + description + " " + index));
    }


}
