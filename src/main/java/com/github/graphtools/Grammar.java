package com.github.graphtools;

import com.fasterxml.jackson.annotation.JsonRootName;
import lombok.Data;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
            if (mappedLeftIndices.contains(embedding.getLeftVertex())) {
                throw new IllegalArgumentException(
                        "Embedding leftVertex must be deleted (unmapped): "
                                + embedding.getLeftVertex() + " in production " + productionName);
            }
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
        boolean forbidDanglingEdges = shouldForbidDanglingEdges();
        Graph product = new Graph(startGraph);
        Map<String, Production> productionsByName = indexProductionsByName();

        for (String productionName : productionNames) {
            Production production = productionsByName.get(productionName);
            if (production == null) {
                throw new IllegalArgumentException(
                        "Production does not exist: " + productionName);
            }
            boolean productionForbidsDanglingEdges =
                    production.getDanglingEdges() == null
                            ? forbidDanglingEdges
                            : shouldForbidDanglingEdges(production.getDanglingEdges());
            applyProduction(product, production, productionForbidsDanglingEdges);
        }

        return product;
    }

    private void applyProduction(
            GraphStructure graph, Production production, boolean forbidDanglingEdges) {
        Map<Integer, Integer> leftToGraph = graph.findSubGraphMapping(production.getLeft())
                .orElse(null);
        if (leftToGraph == null) {
            return;
        }

        applyProduction(graph, production, leftToGraph, forbidDanglingEdges);
    }

    void applyProduction(
            GraphStructure graph, Production production,
            Map<Integer, Integer> leftToGraph, boolean forbidDanglingEdges) {

        Map<Integer, Integer> rightToGraph =
                addRightVerticesAndBuildMapping(graph, production, leftToGraph);

        removeLeftSideAndHandleContextEdges(
                graph, production, leftToGraph, rightToGraph, forbidDanglingEdges);

        updateRightSideLabels(graph, production, rightToGraph);
        addRightSideEdges(graph, production, rightToGraph);
    }

    private Map<Integer, Integer> addRightVerticesAndBuildMapping(
            GraphStructure graph, Production production,
            Map<Integer, Integer> leftToGraph) {

        List<Mapping> mappings = production.getMappings();
        List<Vertex> newRightVertices = production.getRight().getVertices().stream()
                .filter(rightVertex -> mappings.stream()
                        .noneMatch(mapping -> mapping.right() == rightVertex.getIndex()))
                .toList();

        List<Integer> newGraphIndices = graph.getAvailableIndices(newRightVertices.size());
        Map<Integer, Integer> rightToGraph = new HashMap<>();

        for (int i = 0; i < newRightVertices.size(); i++) {
            Vertex rightVertex = newRightVertices.get(i);
            int graphIndex = newGraphIndices.get(i);
            graph.addVertex(new Vertex(graphIndex, rightVertex.getLabel(),
                    rightVertex.getX(), rightVertex.getY()));
            rightToGraph.put(rightVertex.getIndex(), graphIndex);
        }

        for (Mapping mapping : mappings) {
            int graphIndex = getMappedIndexOrThrow(
                    leftToGraph, mapping.left(), "left vertex");
            requireVertex(production.getRight(), mapping.right(), "mapped right vertex");
            rightToGraph.put(mapping.right(), graphIndex);
        }

        return rightToGraph;
    }

    private void updateRightSideLabels(
            GraphStructure graph, Production production,
            Map<Integer, Integer> rightToGraph) {
        for (Vertex rightVertex : production.getRight().getVertices()) {
            int graphIndex = getMappedIndexOrThrow(
                    rightToGraph, rightVertex.getIndex(), "right vertex");
            graph.changeLabel(graphIndex, rightVertex.getLabel());
        }
    }

    private void addRightSideEdges(
            GraphStructure graph, Production production,
            Map<Integer, Integer> rightToGraph) {
        for (Edge rightEdge : production.getRight().getEdges()) {
            Edge edge = new Edge(
                    getMappedIndexOrThrow(rightToGraph, rightEdge.getU(), "right edge source"),
                    getMappedIndexOrThrow(rightToGraph, rightEdge.getV(), "right edge target"),
                    rightEdge.getWeight(),
                    rightEdge.getLabel()
            );
            graph.addEdge(edge);
        }
    }

    private void removeLeftSideAndHandleContextEdges(
            GraphStructure graph, Production production,
            Map<Integer, Integer> leftToGraph,
            Map<Integer, Integer> rightToGraph,
            boolean forbidDanglingEdges) {

        removeMatchedLeftEdges(graph, production.getLeft(), leftToGraph);

        List<Vertex> removedLeftVertices = getUnmappedLeftVertices(production);
        Set<Integer> removedGraphIndices = removedLeftVertices.stream()
                .map(vertex -> getMappedIndexOrThrow(
                        leftToGraph, vertex.getIndex(), "left vertex"))
                .collect(Collectors.toSet());

        rewireEmbeddedContextEdges(
                graph, production, leftToGraph, rightToGraph, removedGraphIndices);
        handleDanglingContextEdges(graph, removedGraphIndices, forbidDanglingEdges);
        removeVertices(graph, removedLeftVertices, leftToGraph);
    }

    private void removeMatchedLeftEdges(
            GraphStructure graph, GraphStructure left, Map<Integer, Integer> leftToGraph) {
        for (Edge leftEdge : left.getEdges()) {
            int graphU = getMappedIndexOrThrow(
                    leftToGraph, leftEdge.getU(), "left edge source");
            int graphV = getMappedIndexOrThrow(
                    leftToGraph, leftEdge.getV(), "left edge target");
            Edge matchedEdge = graph.getEdges().stream()
                    .filter(edge -> isMatchingEdge(
                            graph.isDirected(), edge, graphU, graphV, leftEdge.getLabel()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Matched left edge is missing from the graph"));
            graph.removeEdge(matchedEdge);
        }
    }

    private List<Vertex> getUnmappedLeftVertices(Production production) {
        List<Mapping> mappings = production.getMappings();
        return production.getLeft().getVertices().stream()
                .filter(vertex -> mappings.stream()
                        .noneMatch(mapping -> mapping.left() == vertex.getIndex()))
                .toList();
    }

    /**
     * Reattaches matching context edges from deleted left-side vertices to the
     * right-side vertices named by each embedding's {@code connectTo} indices.
     */
    private void rewireEmbeddedContextEdges(
            GraphStructure graph,
            Production production,
            Map<Integer, Integer> leftToGraph,
            Map<Integer, Integer> rightToGraph,
            Set<Integer> removedGraphIndices) {
        List<Embedding> embeddings = production.getEmbeddings();
        if (embeddings == null) {
            return;
        }

        for (Embedding embedding : embeddings) {
            int deletedGraphIndex = getMappedIndexOrThrow(
                    leftToGraph, embedding.getLeftVertex(), "embedding left vertex");
            if (!removedGraphIndices.contains(deletedGraphIndex)) {
                continue;
            }

            List<Integer> replacementGraphIndices = findEmbeddingTargetGraphIndices(
                    production, rightToGraph, embedding);
            for (Edge contextEdge : new ArrayList<>(graph.getEdges())) {
                if (!isIncidentTo(contextEdge, deletedGraphIndex)) {
                    continue;
                }

                int neighborGraphIndex = getOppositeEndpoint(contextEdge, deletedGraphIndex);
                Vertex neighbor = requireVertex(graph, neighborGraphIndex, "context neighbor");
                if (!"*".equals(embedding.getNeighborLabel())
                        && !Objects.equals(neighbor.getLabel(), embedding.getNeighborLabel())) {
                    continue;
                }
                graph.removeEdge(contextEdge);
                for (int replacementGraphIndex : replacementGraphIndices) {
                    graph.addEdge(rewireEdge(
                            contextEdge, deletedGraphIndex, replacementGraphIndex));
                }
            }
        }
    }

    private static boolean isIncidentTo(Edge edge, int vertexIndex) {
        return edge.getU() == vertexIndex || edge.getV() == vertexIndex;
    }

    private static int getOppositeEndpoint(Edge edge, int vertexIndex) {
        return edge.getU() == vertexIndex ? edge.getV() : edge.getU();
    }

    private static Edge rewireEdge(Edge edge, int oldIndex, int newIndex) {
        return new Edge(
                edge.getU() == oldIndex ? newIndex : edge.getU(),
                edge.getV() == oldIndex ? newIndex : edge.getV(),
                edge.getWeight(),
                edge.getLabel());
    }

    private void handleDanglingContextEdges(
            GraphStructure graph, Set<Integer> removedGraphIndices, boolean forbidDanglingEdges) {
        List<Edge> danglingContextEdges = graph.getEdges().stream()
                .filter(edge -> removedGraphIndices.contains(edge.getU())
                        || removedGraphIndices.contains(edge.getV()))
                .toList();
        if (!forbidDanglingEdges) {
            danglingContextEdges.forEach(graph::removeEdge);
            return;
        }
        if (!danglingContextEdges.isEmpty()) {
            throw new IllegalStateException(
                    "Production cannot remove vertices with unembedded context edges");
        }
    }

    private void removeVertices(
            GraphStructure graph, List<Vertex> leftVertices, Map<Integer, Integer> leftToGraph) {
        for (Vertex leftVertex : leftVertices) {
            int graphIndex = getMappedIndexOrThrow(
                    leftToGraph, leftVertex.getIndex(), "left vertex");
            graph.removeVertex(new Vertex(graphIndex, leftVertex.getLabel()));
        }
    }

    /** Validates the configured dangling-edge policy and returns whether it is {@code forbid}. */
    private boolean shouldForbidDanglingEdges() {
        return shouldForbidDanglingEdges(danglingEdges);
    }

    static boolean shouldForbidDanglingEdges(String danglingEdges) {
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

    private static List<Integer> findEmbeddingTargetGraphIndices(
            Production production,
            Map<Integer, Integer> rightToGraph,
            Embedding embedding) {
        List<Integer> rightVertexIndices = embedding.getConnectTo();
        if (rightVertexIndices == null) {
            throw new IllegalArgumentException(
                    "Embedding connectTo must not be null");
        }
        return rightVertexIndices.stream()
                .map(rightVertexIndex -> {
                    requireVertex(production.getRight(), rightVertexIndex, "embedding target vertex");
                    return getMappedIndexOrThrow(
                            rightToGraph, rightVertexIndex, "embedding target vertex");
                })
                .toList();
    }

    private static Vertex requireVertex(GraphStructure graph, int index, String description) {
        return graph.getVertices().stream()
                .filter(vertex -> vertex.getIndex() == index)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Missing " + description + " " + index));
    }

    private static int getMappedIndexOrThrow(
            Map<Integer, Integer> mapping, int index, String description) {
        Integer graphIndex = mapping.get(index);
        if (graphIndex == null) {
            throw new IllegalArgumentException("Missing mapping for " + description + " " + index);
        }
        return graphIndex;
    }

    private static boolean isMatchingEdge(
            boolean directed, Edge edge, int u, int v, String label) {
        boolean endpointsMatch = edge.getU() == u && edge.getV() == v;
        if (!directed) {
            endpointsMatch |= edge.getU() == v && edge.getV() == u;
        }
        return endpointsMatch && Objects.equals(edge.getLabel(), label);
    }

    private Map<String, Production> indexProductionsByName() {
        return productions.stream()
                .collect(Collectors.toMap(
                        Production::getName,
                        p -> p
                ));
    }

}
