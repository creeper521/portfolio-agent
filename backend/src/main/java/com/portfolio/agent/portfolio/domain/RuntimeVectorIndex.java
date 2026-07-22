package com.portfolio.agent.portfolio.domain;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RuntimeVectorIndex {

    private final int dimension;
    private final Map<String, float[]> vectors;

    public RuntimeVectorIndex(int dimension, Map<String, float[]> vectors) {
        this.dimension = dimension;
        Map<String, float[]> copies = new LinkedHashMap<>();
        for (Map.Entry<String, float[]> entry : vectors.entrySet()) {
            copies.put(entry.getKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
        }
        this.vectors = Collections.unmodifiableMap(copies);
    }

    public int getDimension() { return dimension; }

    public Map<String, float[]> getVectors() {
        Map<String, float[]> copies = new LinkedHashMap<>();
        for (Map.Entry<String, float[]> entry : vectors.entrySet()) {
            copies.put(entry.getKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
        }
        return Collections.unmodifiableMap(copies);
    }
}
