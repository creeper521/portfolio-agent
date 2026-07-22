package com.portfolio.agent.answer.adapter.retrieval;

import com.portfolio.agent.answer.domain.EmbeddingVector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingPostProcessorTest {

    @Test
    void meanPoolsOnlyAttendedTokensAndL2Normalizes() {
        float[][] hiddenState = {
                {3.0f, 0.0f},
                {0.0f, 4.0f},
                {100.0f, 100.0f}
        };

        EmbeddingVector vector = new EmbeddingPostProcessor()
                .meanPoolAndNormalize(hiddenState, new long[]{1, 1, 0});

        assertThat(vector.copyValues()).containsExactly(0.6f, 0.8f);
    }

    @Test
    void rejectsEmptyAttentionAndNonFiniteModelOutput() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                new EmbeddingPostProcessor().meanPoolAndNormalize(
                        new float[][]{{1.0f, 2.0f}}, new long[]{0})))
                .isInstanceOf(com.portfolio.agent.answer.service.LocalEmbeddingFailureException.class);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                new EmbeddingPostProcessor().meanPoolAndNormalize(
                        new float[][]{{Float.NaN, 2.0f}}, new long[]{1})))
                .isInstanceOf(com.portfolio.agent.answer.service.LocalEmbeddingFailureException.class);
    }
}
