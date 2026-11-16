package com.github.graphtools.model;

import com.fasterxml.jackson.annotation.JsonRootName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.Graph;
import org.jgrapht.GraphMapping;
import org.jgrapht.alg.isomorphism.VF2SubgraphIsomorphismInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.jgrapht.graph.SimpleGraph;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A graph represented by indexed vertices, labeled edges, and a directedness flag.
 * Vertex indices are used as stable identifiers by edges and production mappings.
 */
@Slf4j
@Data
@NoArgsConstructor
@JsonRootName("graphStructure")
public class GraphStructure {

    private List<Vertex> vertices;
    private List<Edge> edges;
    private Boolean directed;

    public boolean isDirected() {
        return Boolean.TRUE.equals(directed);
    }

    /** Creates a deep copy of the graph's vertices and edges. */
    public GraphStructure(GraphStructure graphStructure) {

        if (graphStructure.getVertices() != null) {
            this.vertices = graphStructure.getVertices().stream()
                    .map(vertex -> new Vertex(vertex.getIndex(), vertex.getLabel(),
                            vertex.getX(), vertex.getY()))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        if (graphStructure.getEdges() != null) {
            this.edges = graphStructure.getEdges().stream()
                    .map(edge -> new Edge(edge.getU(), edge.getV(), edge.getWeight(), edge.getLabel()))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        this.directed = graphStructure.isDirected();
    }

    /** Parses a graph structure from JSON containing a {@code graphStructure} root. */
    public static GraphStructure fromJson(String json) {
        try {
            final JsonMapper mapper = JsonMapper
                    .builder()
                    .enable(DeserializationFeature.UNWRAP_ROOT_VALUE)
                    .build();
            GraphStructure graph = mapper.readValue(json, GraphStructure.class);
            graph.validate();
            return graph;
        } catch (JacksonException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    /**
     * Checks that this graph has initialized collections, unique vertex indices,
     * and no edges pointing to missing vertices.
     *
     * @throws IllegalArgumentException if the graph is malformed
     */
    public void validate() {
        if (vertices == null || edges == null) {
            throw new IllegalArgumentException("Graph vertices and edges must be provided");
        }

        Set<Integer> vertexIndices = new java.util.HashSet<>();
        for (Vertex vertex : vertices) {
            if (vertex == null) {
                throw new IllegalArgumentException("Graph must not contain null vertices");
            }
            if (!vertexIndices.add(vertex.getIndex())) {
                throw new IllegalArgumentException(
                        "Duplicate vertex index: " + vertex.getIndex());
            }
        }

        for (Edge edge : edges) {
            if (edge == null) {
                throw new IllegalArgumentException("Graph must not contain null edges");
            }
            if (!vertexIndices.contains(edge.getU()) || !vertexIndices.contains(edge.getV())) {
                throw new IllegalArgumentException(
                        "Edge endpoints must reference existing vertices: "
                                + edge.getU() + " -> " + edge.getV());
            }
        }
    }

    /** Changes the label of the vertex with the given index. */
    public GraphStructure changeLabel(int index, String label) {
        Vertex vertex = vertices.stream()
                .filter(v -> v.getIndex() == index)
                .findFirst()
                .orElse(null);

        if (vertex == null) {
            log.error("changeLabel:: Vertex with index:: {} does not exist", index);
            return this;
        }

        vertex.setLabel(label);
        return this;
    }

    /** Adds a vertex to this graph and returns this graph. */
    public GraphStructure addVertex(Vertex vertex) {
        vertices.add(vertex);
        return this;
    }

    /**
     * Removes the vertex with the given index and all edges incident to it.
     *
     * @param vertex vertex identifying the index to remove
     * @return this graph
     */
    public GraphStructure removeVertex(Vertex vertex) {
        Objects.requireNonNull(vertex, "vertex must not be null");
        int index = vertex.getIndex();
        vertices.removeIf(existing -> existing.getIndex() == index);
        edges.removeIf(edge -> edge.getU() == index || edge.getV() == index);
        return this;
    }

    /**
     * Adds an edge if both endpoint indices exist in this graph.
     * Invalid endpoint indices are logged and leave the graph unchanged.
     */
    public GraphStructure addEdge(Edge edge) {
        if (!containsIndices(List.of(edge.getU()))) {
            log.error("addEdge::U:: Vertex with index:: {} does not exist", edge.getU());
            return this;
        }

        if (!containsIndices(List.of(edge.getV()))) {
            log.error("addEdge::V:: Vertex with index:: {} does not exist", edge.getV());
            return this;
        }

        edges.add(edge);
        return this;
    }

    /** Removes the first edge equal to the supplied edge. */
    public GraphStructure removeEdge(Edge edge) {
        edges.remove(edge);
        return this;
    }

    /** Returns whether every supplied index identifies a vertex in this graph. */
    public boolean containsIndices(List<Integer> indices) {
        return indices.stream()
                .allMatch(i -> vertices.stream()
                        .anyMatch(v -> v.getIndex() == i)
                );
    }

    /** Returns whether this graph contains vertices with all the supplied labels. */
    public boolean containsVertices(List<Vertex> givenVertices) {
        return givenVertices.stream()
                .allMatch(given -> vertices.stream()
                        .anyMatch(existing -> Objects.equals(existing.getLabel(), given.getLabel())));
    }

    /**
     * Returns whether each supplied edge is represented by an edge between vertices
     * with matching labels. This check treats endpoint order as interchangeable.
     */
    public boolean containsEdges(List<Edge> givenEdges, List<Vertex> givenVertices) {
        return givenEdges.stream()
                .allMatch(edge -> {
                    String sourceLabel = getLabelOfIndex(edge.getU(), givenVertices);
                    String targetLabel = getLabelOfIndex(edge.getV(), givenVertices);

                    return edges.stream()
                            .anyMatch(existing -> {
                                String existingSourceLabel = getLabelOfIndex(existing.getU());
                                String existingTargetLabel = getLabelOfIndex(existing.getV());
                                return (Objects.equals(sourceLabel, existingSourceLabel)
                                        && Objects.equals(targetLabel, existingTargetLabel))
                                        || (Objects.equals(sourceLabel, existingTargetLabel)
                                        && Objects.equals(targetLabel, existingSourceLabel));
                            });
                });
    }

    private String getLabelOfIndex(int index) {
        return vertices.stream()
                .filter(v -> v.getIndex() == index)
                .map(Vertex::getLabel)
                .findFirst()
                .orElse(null);
    }

    private static String getLabelOfIndex(int index, List<Vertex> givenVertices) {
        return givenVertices.stream()
                .filter(v -> v.getIndex() == index)
                .map(Vertex::getLabel)
                .findFirst()
                .orElse(null);
    }

    private List<Integer> getIndices() {
        return vertices.stream()
                .map(Vertex::getIndex)
                .toList();
    }

    /** Returns {@code n} randomly generated indices not currently used by vertices. */
    public List<Integer> getAvailableIndices(int n) {
        List<Integer> indices = getIndices();
        List<Integer> candidates = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            int candidate;
            do {
                candidate = new Random().nextInt(Integer.MAX_VALUE) + 1;
            } while (indices.contains(candidate) || candidates.contains(candidate));

            candidates.add(candidate);
        }

        return candidates;
    }

    /**
     * Finds a label- and structure-preserving subgraph match.
     *
     * @param subGraph pattern to find in this graph
     * @return a mapping from subgraph vertex indices to this graph's indices,
     *         or empty if no match exists
     */
    public Optional<Map<Integer, Integer>> findSubGraphMapping(GraphStructure subGraph) {
        return findSubGraphMapping(subGraph, false).stream().findFirst();
    }

    /**
     * Finds one or all distinct subgraph mappings.
     *
     * @param subGraph pattern to find in this graph
     * @param many whether to find all mappings
     * @return mappings from subgraph vertex indices to this graph's indices
     */
    public List<Map<Integer, Integer>> findSubGraphMapping(
            GraphStructure subGraph, boolean many) {
        Objects.requireNonNull(subGraph, "subGraph must not be null");
        if (isDirected() != subGraph.isDirected()) {
            return List.of();
        }

        Graph<IndexedVertex, DefaultEdge> hostGraph = toJGraphTGraph();
        Graph<IndexedVertex, DefaultEdge> patternGraph = subGraph.toJGraphTGraph();
        Comparator<IndexedVertex> vertexComparator =
                Comparator.comparingInt(IndexedVertex::type)
                        .thenComparing(IndexedVertex::label, Comparator.nullsFirst(String::compareTo));

        VF2SubgraphIsomorphismInspector<IndexedVertex, DefaultEdge> inspector =
                new VF2SubgraphIsomorphismInspector<>(hostGraph, patternGraph, vertexComparator, null);
        Iterator<GraphMapping<IndexedVertex, DefaultEdge>> mappings = inspector.getMappings();
        Set<Map<Integer, Integer>> results = new java.util.LinkedHashSet<>();
        while (mappings.hasNext()) {
            GraphMapping<IndexedVertex, DefaultEdge> graphMapping = mappings.next();
            Map<Integer, Integer> mapping = new LinkedHashMap<>();
            for (IndexedVertex patternVertex : patternGraph.vertexSet()) {
                if (patternVertex.type() != 0) {
                    continue;
                }
                IndexedVertex hostVertex =
                        graphMapping.getVertexCorrespondence(patternVertex, false);
                if (hostVertex == null) {
                    return List.of();
                }
                mapping.put(patternVertex.index(), hostVertex.index());
            }
            results.add(Map.copyOf(mapping));
            if (!many) {
                break;
            }
        }
        return List.copyOf(results);
    }

    /** Returns whether the supplied graph has a matching subgraph in this graph. */
    public boolean containsSubGraph(GraphStructure graphStructure) {
        return findSubGraphMapping(graphStructure).isPresent();
    }

    private Graph<IndexedVertex, DefaultEdge> toJGraphTGraph() {
        Graph<IndexedVertex, DefaultEdge> graph = isDirected()
                ? new SimpleDirectedGraph<>(DefaultEdge.class)
                : new SimpleGraph<>(DefaultEdge.class);
        Map<Integer, IndexedVertex> verticesByIndex = new HashMap<>();

        for (int i = 0; i < vertices.size(); i++) {
            Vertex vertex = vertices.get(i);
            IndexedVertex indexedVertex = new IndexedVertex(0, i, vertex.getIndex(), vertex.getLabel());
            verticesByIndex.put(vertex.getIndex(), indexedVertex);
            graph.addVertex(indexedVertex);
        }

        for (int i = 0; i < edges.size(); i++) {
            Edge edge = edges.get(i);
            IndexedVertex source = verticesByIndex.get(edge.getU());
            IndexedVertex target = verticesByIndex.get(edge.getV());
            if (source == null || target == null) {
                throw new IllegalArgumentException("Each edge must reference an existing vertex");
            }
            IndexedVertex edgeVertex = new IndexedVertex(1, i, i, edge.getLabel());
            graph.addVertex(edgeVertex);
            if (isDirected()) {
                graph.addEdge(source, edgeVertex);
                graph.addEdge(edgeVertex, target);
            } else if (source.equals(target)) {
                IndexedVertex loopMarker = new IndexedVertex(2, i, i, "");
                graph.addVertex(loopMarker);
                graph.addEdge(source, edgeVertex);
                graph.addEdge(edgeVertex, loopMarker);
                graph.addEdge(loopMarker, source);
            } else {
                graph.addEdge(source, edgeVertex);
                graph.addEdge(edgeVertex, target);
            }
        }
        return graph;
    }

    private record IndexedVertex(int type, int position, int index, String label) {}
}
