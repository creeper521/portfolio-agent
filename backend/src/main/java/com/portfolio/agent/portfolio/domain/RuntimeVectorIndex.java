package com.portfolio.agent.portfolio.domain;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 向量检索索引：chunkId 到 embedding 向量的映射。
 *
 * <p>dimension 声明向量维度（与 {@link RetrievalManifest#getDimension} 一致）。
 * 由于 float[] 本身可变，构造器与 {@code getVectors()} 都对数组逐条做防御性拷贝，
 * 防止外部持有引用后修改内部状态，破坏索引不可变性。
 */
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
