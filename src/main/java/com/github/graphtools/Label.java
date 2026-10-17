package com.github.graphtools;

import lombok.Data;

import java.util.List;

@Data
public class Label {
    List<String> nonterminals;
    List<String> terminals;
}
