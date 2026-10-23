package com.github.graphtools;


import lombok.Data;

@Data
public class HistoryElement {
    private String productionName;
    private Graph graph;

    public HistoryElement(String productionName, Graph graph) {
        this.productionName = productionName;
        this.graph = new Graph(graph);
    }
}
