package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection;

import java.util.Objects;
import java.util.List;

public final class PortfolioSelectionResult {

    private final String releaseVersion;
    private final RetrievalMode retrievalMode;
    private final PortfolioSelection selection;
    private final List<SelectionCandidate> candidatePool;
    private final String reasonCode;

    public PortfolioSelectionResult(
            String releaseVersion,
            RetrievalMode retrievalMode,
            PortfolioSelection selection) {
        this(releaseVersion, retrievalMode, selection, selection.getCandidates(), null);
    }

    public PortfolioSelectionResult(
            String releaseVersion,
            RetrievalMode retrievalMode,
            PortfolioSelection selection,
            List<SelectionCandidate> candidatePool,
            String reasonCode) {
        this.releaseVersion = Objects.requireNonNull(releaseVersion, "releaseVersion");
        this.retrievalMode = Objects.requireNonNull(retrievalMode, "retrievalMode");
        this.selection = Objects.requireNonNull(selection, "selection");
        this.candidatePool = List.copyOf(Objects.requireNonNull(candidatePool, "candidatePool"));
        this.reasonCode = reasonCode;
    }

    public String getReleaseVersion() {
        return releaseVersion;
    }

    public RetrievalMode getRetrievalMode() {
        return retrievalMode;
    }

    public PortfolioSelection getSelection() {
        return selection;
    }

    public List<SelectionCandidate> getCandidatePool() {
        return candidatePool;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}
