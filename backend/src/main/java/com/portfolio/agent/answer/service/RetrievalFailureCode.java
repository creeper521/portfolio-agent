package com.portfolio.agent.answer.service;

import com.portfolio.agent.common.observability.DiagnosticCode;

public enum RetrievalFailureCode implements DiagnosticCode {
    RETRIEVAL_INFERENCE_FAILED,
    RETRIEVAL_VECTOR_DIMENSION_MISMATCH,
    RETRIEVAL_MODEL_LOAD_FAILED,
    RETRIEVAL_EMBEDDING_DISABLED;

    public static RetrievalFailureCode fromLocalEmbeddingCode(String localCode) {
        return switch (localCode) {
            case "LOCAL_INFERENCE_FAILED",
                    "MODEL_OUTPUT_SHAPE_INVALID",
                    "MODEL_OUTPUT_DIMENSION_INVALID",
                    "MODEL_OUTPUT_NON_FINITE",
                    "MODEL_OUTPUT_EMPTY",
                    "MODEL_OUTPUT_NORM_INVALID",
                    "DOCUMENT_TEXT_REQUIRED" -> RETRIEVAL_INFERENCE_FAILED;
            case "VECTOR_DIMENSION_MISMATCH" -> RETRIEVAL_VECTOR_DIMENSION_MISMATCH;
            case "LOCAL_MODEL_DIRECTORY_REQUIRED",
                    "LOCAL_MODEL_DIRECTORY_INVALID",
                    "LOCAL_MODEL_DESCRIPTOR_MISSING",
                    "LOCAL_MODEL_DESCRIPTOR_INVALID",
                    "LOCAL_MODEL_ARTIFACT_MISMATCH",
                    "TOKENIZER_FILE_MISSING",
                    "LOCAL_MODEL_INITIALIZATION_FAILED",
                    "MODEL_FILE_MISSING",
                    "LOCAL_MODEL_CLOSE_FAILED" -> RETRIEVAL_MODEL_LOAD_FAILED;
            case "LOCAL_EMBEDDING_DISABLED" -> RETRIEVAL_EMBEDDING_DISABLED;
            default -> throw new IllegalArgumentException(
                    "unknown local embedding failure code");
        };
    }

    @Override
    public String code() {
        return name();
    }
}
