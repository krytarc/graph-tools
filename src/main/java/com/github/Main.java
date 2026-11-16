package com.github;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.github.graphtools.execution.TransformationSystem;
import com.github.graphtools.grammar.Grammar;
import com.github.graphtools.visualization.HtmlHistoryRenderer;
import java.nio.file.Path;
import com.github.graphtools.io.FileContentReader;
import com.github.graphtools.model.Graph;
import com.github.graphtools.model.GraphStructure;

public class Main {
    public static void main(String[] args) {
//        lab01();

        lab02();
    }

    private static void lab02() {
        try {
            Graph graph = Graph.fromJson(
                    FileContentReader.read("/examples/graph2-01.json"));
            Grammar grammar = Grammar.fromJson(
                    FileContentReader.read("/examples/grammar2-01.json"));

            TransformationSystem system =
                    new TransformationSystem(graph, grammar);
            Graph inputGraph = new Graph(graph);
            system.apply(List.of("P1", "P4", "P6", "P7"), 1, 1);

            Path report = Path.of("target", "graph-history.html");
            HtmlHistoryRenderer.write(
                    inputGraph, grammar, system.getHistory(), report);
            System.out.println("History report: " + report.toAbsolutePath());

            System.out.println(system.getGraph());
            System.out.println(system.getHistory());
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute lab02", e);
        }
    }


    private static void lab01() {
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
        Path report = Path.of("target", "graph.html");
        try {
            HtmlHistoryRenderer.write(graph, report);
            System.out.println("Graph report: " + report.toAbsolutePath());
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to write graph report", e);
        }


//        Graph graph = Graph.fromJson(jsonGraph2);
    }
}
