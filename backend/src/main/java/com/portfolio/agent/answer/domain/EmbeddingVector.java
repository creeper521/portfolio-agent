package com.portfolio.agent.answer.domain;

import java.util.Arrays;

public final class EmbeddingVector {

    private final float[] values;

    public EmbeddingVector(float[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("embedding values are required");
        }
        this.values = Arrays.copyOf(values, values.length);
    }

    public int dimension() { return values.length; }
    public float[] copyValues() { return Arrays.copyOf(values, values.length); }
}
