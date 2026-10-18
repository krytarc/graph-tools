package com.github.graphtools;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Edge {
    private int u;
    private int v;
    private Double weight = 1.0;
    private String Label = "";
}
