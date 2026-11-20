package com.github.graphtools.execution;

import com.github.graphtools.grammar.Embedding;
import com.github.graphtools.grammar.Mapping;
import com.github.graphtools.grammar.Production;
import com.github.graphtools.model.Edge;
import com.github.graphtools.model.GraphStructure;
import com.github.graphtools.model.Vertex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Applies a matched production to a mutable graph. */
public final class ProductionApplier {

    /**
     * Replaces the matched left side and reconnects context edges.
     * The caller supplies a working copy to keep failed steps atomic.
     *
     * @param graph working graph to modify
     * @param production production prepared for the selected execution mode
     * @param leftToGraph mapping from left-side indices to graph indices
     * @param forbidDanglingEdges whether unembedded context edges reject the step
     */
    public void applyProduction(
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
}
