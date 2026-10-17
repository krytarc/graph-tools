package com.github;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.github.graphtools.FileContentReader;
import com.github.graphtools.Model;

public class Main {
    static void main() {
        String graphFile = "/examples/graph01.json";
        String grammarFile = "/examples/grammar01.json";
        String jsonGraph, jsonGrammar;

        try{
            jsonGraph = FileContentReader.read(graphFile);
            jsonGrammar = FileContentReader.read(grammarFile);
        }
        catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }

        Model model = new Model();
        model.loadGraph(jsonGraph).loadGrammar(jsonGrammar);
        System.out.println(model.toString());
    }
}
