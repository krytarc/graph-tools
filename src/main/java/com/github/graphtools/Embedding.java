package com.github.graphtools;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class Embedding {

    int leftVertex = 0;
    String neighborLabel = "";
    List<Integer> connectTo = new ArrayList<>();

}
