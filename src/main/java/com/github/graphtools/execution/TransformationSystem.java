package com.github.graphtools.execution;
import com.github.graphtools.grammar.Grammar;
import com.github.graphtools.history.History;

import com.github.graphtools.model.Graph;
import com.github.graphtools.model.GraphStructure;

import lombok.*;

import java.util.List;

@Data
public class TransformationSystem {
    private final Graph graph;
    private final Grammar grammar;

    @Setter(AccessLevel.NONE)
    private History history = new History();

    public TransformationSystem apply(List<String> productions) {
        return apply(productions, 1, null);
    }

    public TransformationSystem apply(
            List<String> productions, int selectionStrategy) {
        return apply(productions, selectionStrategy, null);
    }

    public TransformationSystem apply(
            List<String> productions, int selectionStrategy,
            Integer danglingEdges) {
        if (selectionStrategy != 1 && selectionStrategy != 2) {
            throw new IllegalArgumentException(
                    "selectionStrategy must be 1 or 2");
        }
        String policy = danglingEdges == null ? null : switch (danglingEdges) {
            case 1 -> "forbid";
            case 2 -> "delete";
            default -> throw new IllegalArgumentException(
                    "danglingEdges must be 1 or 2");
        };
        return execute(productions, selectionStrategy == 1, policy);
    }

    public TransformationSystem apply(
            List<String> productions, String selectionStrategy,
            String danglingEdges) {
        return apply(productions, selectionStrategy, danglingEdges,
                ExecutionMode.EMBEDDING_PRIORITY);
    }

    private TransformationSystem execute(
            List<String> productions, boolean many, String danglingEdges) {
        return execute(productions, many, danglingEdges,
                ExecutionMode.EMBEDDING_PRIORITY);
    }

    /** Applies productions using the selected mapping and embedding semantics. */
    public TransformationSystem apply(
            List<String> productions, String selectionStrategy,
            String danglingEdges, ExecutionMode executionMode) {
        boolean many = selectionStrategy == null
                || switch (selectionStrategy) {
                    case "1", "all" -> true;
                    case "2", "single" -> false;
                    default -> throw new IllegalArgumentException(
                            "selectionStrategy must be 1, 2, all or single");
                };
        String policy = danglingEdges == null ? null : switch (danglingEdges) {
            case "1", "forbid" -> "forbid";
            case "2", "delete" -> "delete";
            default -> throw new IllegalArgumentException(
                    "danglingEdges must be 1, 2, forbid or delete");
        };
        return execute(productions, many, policy, executionMode);
    }

    private TransformationSystem execute(
            List<String> productions, boolean many, String danglingEdges,
            ExecutionMode executionMode) {
        GrammarExecutor executor =
                new GrammarExecutor(
                        graph, grammar, productions, danglingEdges, executionMode);
        for (String production : List.copyOf(productions)) {
            GraphStructure result = executor.next(many, true);
            graph.setVertices(result.getVertices());
            graph.setEdges(result.getEdges());
            graph.setDirected(result.getDirected());
            history.append(production, graph);
        }
        return this;
    }
}
