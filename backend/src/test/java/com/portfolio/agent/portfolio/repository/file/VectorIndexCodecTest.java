package com.portfolio.agent.portfolio.repository.file;

import com.portfolio.agent.portfolio.exception.InvalidPortfolioSnapshotException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorIndexCodecTest {

    private final VectorIndexCodec codec = new VectorIndexCodec();

    @Test
    void roundTripsInStableChunkOrder() {
        Map<String, float[]> first = new LinkedHashMap<>();
        first.put("chunk-b", unit(2, 1));
        first.put("chunk-a", unit(2, 0));
        Map<String, float[]> second = new LinkedHashMap<>();
        second.put("chunk-a", unit(2, 0));
        second.put("chunk-b", unit(2, 1));

        byte[] encoded = codec.encode(first, 2);

        assertThat(codec.encode(second, 2)).isEqualTo(encoded);
        assertThat(codec.decode(encoded, 2).getVectors().keySet())
                .containsExactly("chunk-a", "chunk-b");
    }

    @Test
    void rejectsInvalidDimensionsNonFiniteValuesAndTrailingBytes() {
        assertThatThrownBy(() -> codec.encode(Map.of("chunk", new float[]{1.0f}), 2))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("dimension");
        assertThatThrownBy(() -> codec.encode(
                Map.of("chunk", new float[]{Float.NaN, 0.0f}), 2))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("finite");

        byte[] valid = codec.encode(Map.of("chunk", unit(2, 0)), 2);
        byte[] trailing = java.util.Arrays.copyOf(valid, valid.length + 1);
        assertThatThrownBy(() -> codec.decode(trailing, 2))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("trailing");
    }

    private float[] unit(int dimension, int index) {
        float[] value = new float[dimension];
        value[index] = 1.0f;
        return value;
    }
}
