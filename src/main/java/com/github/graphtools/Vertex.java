package com.github.graphtools;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Random;

@Data
@NoArgsConstructor
public class Vertex {

    private int index = new Random().nextInt();
    private String label = "";

}
