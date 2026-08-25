package com.portfolio.agent.infrastructure.retrieval;

import java.util.Arrays;

/**
 * 本地检索用 embedding 向量：不可变的一维 float 数组载体。
 *
 * <p>构造期要求非空并做防御性拷贝，外部只能通过 {@link #copyValues()}
 * 取得副本，保证向量内容创建后不被篡改（检索结果的可复现性依赖于此）。
 */
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
