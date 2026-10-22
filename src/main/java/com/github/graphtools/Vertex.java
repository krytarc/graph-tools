package com.github.graphtools;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class Vertex {

    private int index;
    private String label = "";
    private Double x;
    private Double y;

    public Vertex(int index, String label) {
        this(index, label, null, null);
    }

}
