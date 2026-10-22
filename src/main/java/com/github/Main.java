package com.github;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.github.graphtools.*;

public class Main {
    public static void main(String[] args) {
        String graphFile = "/examples/graph01.json";
        String graphFile2 = "/examples/graph02.json";
        String grammarFile = "/examples/grammar01.json";
        String jsonGraph, jsonGraph2, jsonGrammar, subgraph;
        String subgraphFile = "/examples/graphstructure1.json";

        try{
            jsonGraph = FileContentReader.read(graphFile);
            jsonGraph2 = FileContentReader.read(graphFile2);
            jsonGrammar = FileContentReader.read(grammarFile);
            subgraph = FileContentReader.read(subgraphFile);

        }
        catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }

        Grammar grammar = Grammar.fromJson(jsonGrammar);
        GraphStructure graph = grammar.executeProductions(List.of("P1", "P1", "P3", "P2", "P2"));
        GraphVisualizer.show(graph, "Graf wynikowy");


//        Graph graph = Graph.fromJson(jsonGraph2);
//        GraphVisualizer.show(graph, "Graph");
    }
}
