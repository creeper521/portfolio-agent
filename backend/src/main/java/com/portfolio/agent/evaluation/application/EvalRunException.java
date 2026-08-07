package com.portfolio.agent.evaluation.application;

public final class EvalRunException extends RuntimeException {

    public EvalRunException(String message) {
        super(message);
    }

    public EvalRunException(String message, Throwable cause) {
        super(message, cause);
    }
}
