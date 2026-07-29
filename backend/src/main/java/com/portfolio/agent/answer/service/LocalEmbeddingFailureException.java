package com.portfolio.agent.answer.service;

public final class LocalEmbeddingFailureException extends RuntimeException {

    private final RetrievalFailureCode code;

    public LocalEmbeddingFailureException(String code) {
        super(code);
        this.code = RetrievalFailureCode.fromLocalEmbeddingCode(code);
    }

    public LocalEmbeddingFailureException(RetrievalFailureCode code) {
        super(code.code());
        this.code = code;
    }

    public RetrievalFailureCode getCode() { return code; }
}
