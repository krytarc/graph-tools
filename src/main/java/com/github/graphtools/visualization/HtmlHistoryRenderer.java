package com.github.graphtools.visualization;

import com.github.graphtools.grammar.Grammar;
import com.github.graphtools.grammar.Production;
import com.github.graphtools.history.History;
import com.github.graphtools.model.GraphStructure;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Exports a complete execution history as a self-contained HTML report. */
public final class HtmlHistoryRenderer {
    private HtmlHistoryRenderer() {}

    /** Writes a single graph and its adjacency matrix as an HTML report. */
    public static void write(GraphStructure graph, Path output) throws IOException {
        writeSteps(List.of(new Step("Result graph", graph, null)), output);
    }

    /** A graph snapshot and the production responsible for that step. */
    public record Step(
            String title, GraphStructure graph, Production production) {}

    /**
     * Writes the input graph and every recorded step into one HTML document.
     * The report works offline and does not require a web server.
     */
    public static void write(
            GraphStructure input, Grammar grammar,
            History history, Path output) throws IOException {
        List<Step> steps = new ArrayList<>();
        steps.add(new Step("Input graph", input, null));

        if (history.getHistory() != null) {
            int number = 1;
            for (var element : history.getHistory()) {
                Production production = grammar.getProductions().stream()
                        .filter(p -> p.getName().equals(element.getProductionName()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Unknown production: " + element.getProductionName()));
                steps.add(new Step(
                        "Step " + number++ + ": " + production.getName(),
                        element.getGraph(), production));
            }
        }

        writeSteps(steps, output);
    }

    private static void writeSteps(List<Step> steps, Path output) throws IOException {
        // Prevent labels from terminating the script element containing the data.
        String json = new JsonMapper().writeValueAsString(steps)
                .replace("&", "\\u0026")
                .replace("<", "\\u003c")
                .replace(">", "\\u003e");

        String template;
        try (var stream = HtmlHistoryRenderer.class.getResourceAsStream(
                "/visualization/history.html")) {
            if (stream == null) {
                throw new IOException("History report template is missing");
            }
            template = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        Path destination = output.toAbsolutePath();
        Files.createDirectories(destination.getParent());
        Files.writeString(
                destination, template.replace("__REPORT_DATA__", json),
                StandardCharsets.UTF_8);
    }
}
