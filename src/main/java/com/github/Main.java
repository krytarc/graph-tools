package com.github;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.github.graphtools.*;

public class Main {
    public static void main(String[] args) {
        String graphFile = "/examples/graph01.json";
        String grammarFile = "/examples/grammar01.json";
        String jsonGraph, jsonGrammar, subgraph;
        String subgraphFile = "/examples/graphstructure1.json";

        try{
            jsonGraph = FileContentReader.read(graphFile);
            jsonGrammar = FileContentReader.read(grammarFile);
            subgraph = FileContentReader.read(subgraphFile);

        }
        catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }

        Grammar grammar = Grammar.fromJson(jsonGrammar);
        GraphStructure graph = grammar.executeProductions(List.of("P1", "P1", "P3", "P2", "P2"));
        GraphVisualizer.show(graph, "Graf wynikowy");
    }
}
