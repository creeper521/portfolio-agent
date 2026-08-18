package com.portfolio.agent.turn.capability.portfolio.retrieval;

public enum RetrievalAttemptFailure {
    VECTOR_UNAVAILABLE,
    BACKEND_CONNECTION_UNAVAILABLE,
    BACKEND_TIMEOUT,
    CANCELLED,
    CONTENT_RELEASE_MISMATCH,
    INTEGRITY_FAILURE,
    INVALID_REQUEST
}
