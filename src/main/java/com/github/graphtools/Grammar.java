package com.github.graphtools;

import com.fasterxml.jackson.annotation.JsonRootName;
import lombok.Data;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

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

}
