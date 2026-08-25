package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection;

import java.util.List;
import java.util.Objects;

/** 候选检索结果（不可变值对象）：发布版本、实际检索模式（含降级信息）与候选列表。 */
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
