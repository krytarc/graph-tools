package com.github.graphtools;

import com.fasterxml.jackson.annotation.JsonRootName;
import lombok.Data;
import lombok.NoArgsConstructor;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

@Data
@NoArgsConstructor
@JsonRootName("graphStructure")
public class GraphStructure {

    private List<Vertex> vertices;
    private List<Edge> edges;
    private boolean directed;

    public GraphStructure(GraphStructure graphStructure) {
        this.vertices = new ArrayList<>(graphStructure.getVertices());
        this.edges = new ArrayList<>(graphStructure.getEdges());
        this.directed = graphStructure.isDirected();
    }

    public static GraphStructure fromJson(String json) {
        try {
            final JsonMapper mapper = JsonMapper
                    .builder()
                    .enable(DeserializationFeature.UNWRAP_ROOT_VALUE)
                    .build();
            return mapper.readValue(json, GraphStructure.class);
        }
        catch (JacksonException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    //TODO if the proper index exists


    public String getLabelOfIndex(int index) {
        return vertices.stream()
                .filter(v -> v.getIndex() == index)
                .map(Vertex::getLabel)
                .findFirst()
                .orElse(null);
    }

    public static String getLabelOfIndex(int index, List<Vertex> givenVertices) {
        return givenVertices.stream()
                .filter(v -> v.getIndex() == index)
                .map(Vertex::getLabel)
                .findFirst()
                .orElse(null);
    }


    public boolean containsVertices(List<Vertex> givenVertices) {
        return givenVertices.stream()
                .allMatch(v1 -> vertices
                        .stream()
                        .anyMatch(v2 -> v2.getLabel().equals(v1.getLabel()))
                );
    }

    public List<Vertex> fixIndices() {
        Set<Integer> usedIndices = new HashSet<>();
        Random random = new Random();

        for (Vertex vertex : vertices) {
            if (!usedIndices.add(vertex.getIndex())) {
                int newIndex;
                do {
                    newIndex = random.nextInt();
                } while (usedIndices.contains(newIndex));

                vertex.setIndex(newIndex);
                usedIndices.add(newIndex);
            }
        }

        return vertices;
    }


    public boolean containsEdges(List<Edge> givenEdges, List<Vertex> givenVertices) {
        return givenEdges.stream()
                .allMatch(e -> {
                    String l1 = GraphStructure.getLabelOfIndex(e.getU(), givenVertices);
                    String l2 = GraphStructure.getLabelOfIndex(e.getV(), givenVertices);

                    return edges.stream()
                            .anyMatch(edge -> {
                                String label1 = getLabelOfIndex(edge.getU());
                                String label2 = getLabelOfIndex(edge.getV());
                                return (l1.equals(label1) && l2.equals(label2)) ||
                                        (l1.equals(label2) && l2.equals(label1));
                            });
                });
    }

    public Optional<Map<Integer, Integer>> findSubGraphMapping(GraphStructure subGraph) {
        Objects.requireNonNull(subGraph, "subGraph must not be null");
        if (directed != subGraph.isDirected()) {
            return Optional.empty();
        }

        Graph<IndexedVertex, DefaultEdge> hostGraph = toJGraphTGraph();
        Graph<IndexedVertex, DefaultEdge> patternGraph = subGraph.toJGraphTGraph();
        Comparator<IndexedVertex> vertexComparator =
                Comparator.comparingInt(IndexedVertex::type)
                        .thenComparing(IndexedVertex::label, Comparator.nullsFirst(String::compareTo));

        VF2SubgraphIsomorphismInspector<IndexedVertex, DefaultEdge> inspector =
                new VF2SubgraphIsomorphismInspector<>(hostGraph, patternGraph, vertexComparator, null);
        Iterator<GraphMapping<IndexedVertex, DefaultEdge>> mappings = inspector.getMappings();
        if (!mappings.hasNext()) {
            return Optional.empty();
        }

        GraphMapping<IndexedVertex, DefaultEdge> graphMapping = mappings.next();
        Map<Integer, Integer> mapping = new LinkedHashMap<>();
        for (IndexedVertex patternVertex : patternGraph.vertexSet()) {
            if (patternVertex.type() != 0) {
                continue;
            }
            IndexedVertex hostVertex = graphMapping.getVertexCorrespondence(patternVertex, false);
            if (hostVertex == null) {
                return Optional.empty();
            }
            mapping.put(patternVertex.index(), hostVertex.index());
        }
        return Optional.of(Map.copyOf(mapping));
    }

    private Graph<IndexedVertex, DefaultEdge> toJGraphTGraph() {
        Graph<IndexedVertex, DefaultEdge> graph = directed
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
            if (directed) {
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

    public boolean containsSubGraph(GraphStructure graphStructure) {
        return findSubGraphMapping(graphStructure).isPresent();
    }

    private record IndexedVertex(int type, int position, int index, String label) {}
}
