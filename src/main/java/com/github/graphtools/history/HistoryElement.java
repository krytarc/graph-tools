package com.github.graphtools.history;

import com.github.graphtools.model.Graph;

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
