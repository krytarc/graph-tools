package com.github.graphtools;

import lombok.Value;

@Value
public class Embedding {

    int leftVertex;
    String neighborLabel = "";
    String connectTo = "";

}
