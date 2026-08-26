package com.portfolio.agent.infrastructure.model.structured;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;
import java.util.function.UnaryOperator;

/** 把已通过 Provider-facing contract 的 Draft 确定性编译为应用合同树。 */
public interface StructuredOutputCompiler {
    String profileVersion();

    JsonNode compile(JsonNode providerDraft);

    static StructuredOutputCompiler identity() {
        return named(OperationBinding.IDENTITY_OUTPUT_COMPILER_VERSION,
                JsonNode::deepCopy);
    }

    static StructuredOutputCompiler named(
            String profileVersion, UnaryOperator<JsonNode> compiler) {
        String requiredVersion = Objects.requireNonNull(
                profileVersion, "profileVersion");
        UnaryOperator<JsonNode> requiredCompiler = Objects.requireNonNull(
                compiler, "compiler");
        return new StructuredOutputCompiler() {
            @Override
            public String profileVersion() {
                return requiredVersion;
            }

            @Override
            public JsonNode compile(JsonNode providerDraft) {
                return requiredCompiler.apply(providerDraft);
            }
        };
    }
}
