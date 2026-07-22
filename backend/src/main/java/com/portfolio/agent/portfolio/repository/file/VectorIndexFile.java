package com.portfolio.agent.portfolio.repository.file;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VectorIndexFile {

    private final int dimension;
    private final Map<String, float[]> vectors;

    public VectorIndexFile(int dimension, Map<String, float[]> vectors) {
        this.dimension = dimension;
        Map<String, float[]> copy = new LinkedHashMap<>();
        for (Map.Entry<String, float[]> entry : vectors.entrySet()) {
            copy.put(entry.getKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
        }
        this.vectors = Collections.unmodifiableMap(copy);
    }

    public int getDimension() { return dimension; }

    public Map<String, float[]> getVectors() {
        Map<String, float[]> copy = new LinkedHashMap<>();
        for (Map.Entry<String, float[]> entry : vectors.entrySet()) {
            copy.put(entry.getKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
        }
        return Collections.unmodifiableMap(copy);
    }
}
