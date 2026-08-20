package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection;

public final class CandidateRetrievalException extends RuntimeException {

    public CandidateRetrievalException(String message) {
        super(message);
    }

    public CandidateRetrievalException(String message, Throwable cause) {
        super(message, cause);
    }
}
