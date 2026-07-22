package com.portfolio.agent.answer.service;

public final class LocalEmbeddingFailureException extends RuntimeException {

    private final String code;

    public LocalEmbeddingFailureException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() { return code; }
}
