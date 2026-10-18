package com.github.graphtools;


import lombok.Data;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;


@Data
public class Model {

    private Grammar grammar;
    private Graph graph;


    public static Model fromJson(String json) {
        try {
            return new JsonMapper().readValue(json, Model.class);
        }
        catch (JacksonException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public Model loadGrammar(String json) {
        grammar = Grammar.fromJson(json);
        return this;
    }

    public Model loadGraph(String json) {
        graph = Graph.fromJson(json);
        return this;
    }




}
