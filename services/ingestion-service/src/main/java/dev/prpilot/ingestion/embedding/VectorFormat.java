package dev.prpilot.ingestion.embedding;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class VectorFormat {

    private VectorFormat() {}

    /** Converts a float array into pgvector's text literal format: [0.1,0.2,0.3] */
    public static String toLiteral(float[] vector) {
        String joined = IntStream.range(0, vector.length)
                .mapToObj(i -> String.valueOf(vector[i]))
                .collect(Collectors.joining(","));
        return "[" + joined + "]";
    }
}