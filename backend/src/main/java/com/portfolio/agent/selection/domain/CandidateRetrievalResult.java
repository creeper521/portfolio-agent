package com.portfolio.agent.selection.domain;

import java.util.List;
import java.util.Objects;

public final class CandidateRetrievalResult {

    private final String releaseVersion;
    private final RetrievalMode retrievalMode;
    private final List<SelectionCandidate> candidates;

    public CandidateRetrievalResult(
            String releaseVersion,
            RetrievalMode retrievalMode,
            List<SelectionCandidate> candidates) {
        this.releaseVersion = Objects.requireNonNull(releaseVersion, "releaseVersion");
        this.retrievalMode = Objects.requireNonNull(retrievalMode, "retrievalMode");
        this.candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
    }

    public String getReleaseVersion() {
        return releaseVersion;
    }

    public RetrievalMode getRetrievalMode() {
        return retrievalMode;
    }

    public List<SelectionCandidate> getCandidates() {
        return candidates;
    }
}
