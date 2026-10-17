package com.github.graphtools;

import lombok.Data;

import java.util.List;

@Data
public class GraphStructure {

    private List<Vertex> vertices;
    private List<Edge> edges;

}
