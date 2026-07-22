package com.portfolio.agent.answer.adapter.retrieval;

import com.portfolio.agent.answer.domain.EmbeddingVector;
import com.portfolio.agent.answer.service.LocalEmbeddingFailureException;

public final class EmbeddingPostProcessor {

    public EmbeddingVector meanPoolAndNormalize(float[][] hiddenState, long[] attentionMask) {
        if (hiddenState == null || hiddenState.length == 0
                || attentionMask == null || attentionMask.length != hiddenState.length) {
            throw new LocalEmbeddingFailureException("MODEL_OUTPUT_SHAPE_INVALID");
        }
        int dimension = hiddenState[0].length;
        if (dimension == 0) {
            throw new LocalEmbeddingFailureException("MODEL_OUTPUT_SHAPE_INVALID");
        }
        double[] sums = new double[dimension];
        int attendedTokens = 0;
        for (int token = 0; token < hiddenState.length; token++) {
            if (hiddenState[token] == null || hiddenState[token].length != dimension) {
                throw new LocalEmbeddingFailureException("MODEL_OUTPUT_SHAPE_INVALID");
            }
            if (attentionMask[token] == 0) {
                continue;
            }
            attendedTokens++;
            for (int index = 0; index < dimension; index++) {
                float value = hiddenState[token][index];
                if (!Float.isFinite(value)) {
                    throw new LocalEmbeddingFailureException("MODEL_OUTPUT_NON_FINITE");
                }
                sums[index] += value;
            }
        }
        if (attendedTokens == 0) {
            throw new LocalEmbeddingFailureException("MODEL_OUTPUT_EMPTY");
        }
        double squaredNorm = 0.0;
        for (int index = 0; index < dimension; index++) {
            sums[index] /= attendedTokens;
            squaredNorm += sums[index] * sums[index];
        }
        double norm = Math.sqrt(squaredNorm);
        if (!Double.isFinite(norm) || norm == 0.0) {
            throw new LocalEmbeddingFailureException("MODEL_OUTPUT_NORM_INVALID");
        }
        float[] normalized = new float[dimension];
        for (int index = 0; index < dimension; index++) {
            normalized[index] = (float) (sums[index] / norm);
        }
        return new EmbeddingVector(normalized);
    }
}
