package com.github.graphtools;

import com.fasterxml.jackson.annotation.JsonRootName;
import lombok.Data;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@JsonRootName("grammar")
public class Grammar {

    String name = "";
    boolean directed = false;
    String danglingEdges = "forbid";

    Label labels;
    Graph startGraph;
    List<Production> productions;

    public static Grammar fromJson(String json) {
        try {
            final JsonMapper mapper = JsonMapper
                    .builder()
                    .enable(DeserializationFeature.UNWRAP_ROOT_VALUE)
                    .build();
            return mapper.readValue(json, Grammar.class);
        }
        catch (JacksonException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public GraphStructure executeProductions(List<String> productions) {
        Graph product = new Graph(startGraph);
        Map<String, Production> productionMapping = getNamedProductions();

        for (var production: productions) {
            if (!productionMapping.containsKey(production))
                throw new IllegalArgumentException("One of given production does not exist");

            GraphStructure leftGraph = productionMapping.get(production).getLeft();
            if (!product.containsSubGraph(leftGraph))
                continue;

            GraphStructure rightGraph = productionMapping.get(production).getRight();
            List<Mapping> mappings = productionMapping.get(production).getMappings();
            List<Embedding> embeddings = productionMapping.get(production).getEmbeddings();

        }

        return product;
    }

    public Map<String, Production> getNamedProductions() {
        return productions.stream()
                .collect(Collectors.toMap(
                        Production::getName,
                        p -> p
                ));
    }

}
