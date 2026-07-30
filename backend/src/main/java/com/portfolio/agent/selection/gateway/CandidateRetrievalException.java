package com.portfolio.agent.selection.gateway;

public final class CandidateRetrievalException extends RuntimeException {

    public CandidateRetrievalException(String message) {
        super(message);
    }

    public CandidateRetrievalException(String message, Throwable cause) {
        super(message, cause);
    }
}
