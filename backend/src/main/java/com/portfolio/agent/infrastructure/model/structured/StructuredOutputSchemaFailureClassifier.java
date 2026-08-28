package com.portfolio.agent.infrastructure.model.structured;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Provider draft schema 失败的消费侧重分类边界。
 *
 * <p>注册表始终先生成不含领域知识的通用 schema failure；只有拥有 wire-shape
 * 语义的消费侧可在不回显 payload 的前提下把它收窄为领域闭集 reason。</p>
 */
@FunctionalInterface
public interface StructuredOutputSchemaFailureClassifier {

    StructuredOutputValidationException classify(
            JsonNode rejectedTree,
            StructuredOutputValidationException genericFailure);

    static StructuredOutputSchemaFailureClassifier generic() {
        return (rejectedTree, genericFailure) -> genericFailure;
    }
}
