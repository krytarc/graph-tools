package com.github.graphtools;


import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class History {

    private List<HistoryElement> history;


    public History append(HistoryElement element) {
        createHistoryIfAbsent();
        history.add(element);
        return this;
    }

    public History append(String production, Graph graph) {
        createHistoryIfAbsent();
        history.add(new HistoryElement(production, graph));
        return this;
    }

    private void createHistoryIfAbsent() {
        if (history == null) {
            history = new ArrayList<>();
        }
    }
}
