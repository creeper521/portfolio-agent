package com.portfolio.agent.turn.capability.portfolio.retrieval;

import java.util.Objects;
import java.util.Optional;

public final class RetrievalAttemptResult {
    private final PortfolioCandidateSet candidateSet;
    private final RetrievalAttemptFailure failure;

    private RetrievalAttemptResult(
            PortfolioCandidateSet candidateSet, RetrievalAttemptFailure failure) {
        this.candidateSet = candidateSet;
        this.failure = failure;
        if ((candidateSet == null) == (failure == null)) {
            throw new IllegalArgumentException("attempt result must contain success or failure only");
        }
    }

    public static RetrievalAttemptResult success(PortfolioCandidateSet candidateSet) {
        return new RetrievalAttemptResult(Objects.requireNonNull(candidateSet, "candidateSet"), null);
    }

    public static RetrievalAttemptResult failure(RetrievalAttemptFailure failure) {
        return new RetrievalAttemptResult(null, Objects.requireNonNull(failure, "failure"));
    }

    public boolean isSuccessful() { return candidateSet != null; }
    public Optional<PortfolioCandidateSet> getCandidateSet() {
        return Optional.ofNullable(candidateSet);
    }
    public Optional<RetrievalAttemptFailure> getFailure() { return Optional.ofNullable(failure); }
}
