package com.github.graphtools;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class Production {

    String name = "";
    String description = "";
    GraphStructure left;
    GraphStructure right;
    @JsonAlias("mapping")
    List<Mapping> mappings = new ArrayList<>();
    @JsonAlias("embedding")
    List<Embedding> embeddings = new ArrayList<>();

}
