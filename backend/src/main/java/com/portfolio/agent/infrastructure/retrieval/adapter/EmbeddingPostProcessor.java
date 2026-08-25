package com.portfolio.agent.infrastructure.retrieval.adapter;

import com.portfolio.agent.infrastructure.retrieval.EmbeddingVector;
import com.portfolio.agent.infrastructure.retrieval.LocalEmbeddingFailureException;

/**
 * embedding 后处理器：对模型输出的 token 级隐状态做注意力掩码均值池化
 * （mean pooling）并做 L2 归一化，得到最终查询/文档向量。
 *
 * <p>这是 BGE 类嵌入模型的标准后处理：只用 attention mask 标记的真实
 * token 参与池化；归一化后向量内积即余弦相似度，便于 pgvector 检索。
 * 每一步形状或数值校验失败都以封闭的 {@link LocalEmbeddingFailureException}
 * code 上抛（形状非法、非有限值、无有效 token、范数非法），不携带原文。
 */
public final class EmbeddingPostProcessor {

    /**
     * 对单条隐状态做均值池化与 L2 归一化。
     *
     * @param hiddenState 形状为 [token][dimension] 的模型隐状态输出
     * @param attentionMask 与 token 数等长的掩码，非 0 表示该 token 有效
     * @return 归一化后的 embedding 向量
     * @throws LocalEmbeddingFailureException 形状不匹配或为空
     *         （MODEL_OUTPUT_SHAPE_INVALID）、出现非有限值
     *         （MODEL_OUTPUT_NON_FINITE）、没有有效 token
     *         （MODEL_OUTPUT_EMPTY）或范数非法（MODEL_OUTPUT_NORM_INVALID）
     */
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
            // 用 double 累加再一次性回写成 float，减少池化与归一化的舍入误差。
            normalized[index] = (float) (sums[index] / norm);
        }
        return new EmbeddingVector(normalized);
    }
}
