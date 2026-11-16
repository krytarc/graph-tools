package com.github.graphtools.grammar;

import lombok.Data;

import java.util.List;

@Data
public class Label {
    List<String> nonterminals;
    List<String> terminals;
}
