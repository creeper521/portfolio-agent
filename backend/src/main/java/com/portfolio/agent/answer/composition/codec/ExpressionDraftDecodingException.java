package com.portfolio.agent.answer.composition.codec;

public final class ExpressionDraftDecodingException extends RuntimeException {
    public ExpressionDraftDecodingException() { super("expression draft is invalid"); }
    public ExpressionDraftDecodingException(Throwable cause) { super("expression draft is invalid", cause); }
}
