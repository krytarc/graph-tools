package com.github.graphtools.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Edge {
    private int u;
    private int v;
    private Double weight = 1.0;
    private String Label = "";
}
