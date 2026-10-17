package com.github.graphtools;

import lombok.Value;

import java.util.List;

@Value
public class Production {

    String name = "";
    String description = "";
    boolean directed = false;
    GraphStructure left;
    GraphStructure right;
    List<Mapping> mappings;
    List<Embedding> embeddings;

}
