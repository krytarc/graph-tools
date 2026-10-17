package com.github.graphtools;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Edge {
    int u;
    int v;
    Double weight = 1.0;
    String Label = "";
}
