package com.github.graphtools;

import com.fasterxml.jackson.annotation.JsonRootName;
import lombok.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;


@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@JsonRootName("graph")
public class Graph extends GraphStructure{

    private String name;

    public Graph(Graph graph) {
        super(graph);
        this.name = graph.getName();
    }

    public static Graph fromJson(String json) {
        try {
            final JsonMapper mapper = JsonMapper
                    .builder()
                    .enable(DeserializationFeature.UNWRAP_ROOT_VALUE)
                    .build();
            return mapper.readValue(json, Graph.class);
        }
        catch (JacksonException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

}
