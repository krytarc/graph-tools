package com.github.graphtools;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class GrammarExecutor {
    private final Graph graph;
    private final Grammar grammar;
    private final String danglingEdges;
    private final List<String> productions;

    public Graph next() {

    }
}
