package com.github.graphtools.execution;
import com.github.graphtools.grammar.Embedding;
import com.github.graphtools.grammar.Production;

import java.util.Set;
import java.util.stream.Collectors;

/** Controls how mappings interact with explicit embedding rules. */
public enum ExecutionMode {
    EMBEDDING_PRIORITY,
    LEGACY;

    /** Returns execution rules without modifying the source production. */
    Production prepare(Production source) {
        Set<Integer> embeddedVertices = source.getEmbeddings().stream()
                .map(Embedding::getLeftVertex)
                .collect(Collectors.toSet());

        boolean overlap = source.getMappings().stream()
                .anyMatch(mapping -> embeddedVertices.contains(mapping.left()));
        if (!overlap) {
            return source;
        }
        if (this == LEGACY) {
            return source;
        }

        Production result = new Production();
        result.setName(source.getName());
        result.setDescription(source.getDescription());
        result.setDanglingEdges(source.getDanglingEdges());
        result.setLeft(source.getLeft());
        result.setRight(source.getRight());
        result.setEmbeddings(source.getEmbeddings());
        result.setMappings(source.getMappings().stream()
                .filter(mapping -> !embeddedVertices.contains(mapping.left()))
                .toList());
        return result;
    }
}
