package com.github.graphtools;

import com.github.Main;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public interface FileContentReader {
    public static String read(String fileName) throws Exception {

        try (InputStream in = Main.class.getResourceAsStream(fileName)) {
            if (in == null) throw new FileNotFoundException();

            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }
}
