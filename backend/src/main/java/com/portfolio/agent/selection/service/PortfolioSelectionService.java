package com.portfolio.agent.selection.service;

import com.portfolio.agent.selection.domain.CandidateRetrievalResult;
import com.portfolio.agent.selection.domain.PortfolioSelection;
import com.portfolio.agent.selection.domain.PortfolioSelectionResult;
import com.portfolio.agent.selection.domain.PortfolioSelectionStatus;
import com.portfolio.agent.selection.domain.RetrievalMode;
import com.portfolio.agent.selection.domain.SelectionScore;
import com.portfolio.agent.selection.domain.SelectionTarget;
import com.portfolio.agent.selection.gateway.CandidateRetrievalPort;
import com.portfolio.agent.selection.gateway.CandidateRetrievalException;
import java.util.List;
import java.util.Objects;

public final class PortfolioSelectionService {

    private static final int CANDIDATE_LIMIT = 12;

    private final CandidateRetrievalPort retrievalPort;
    private final SelectionStrategy selectionStrategy;

    public PortfolioSelectionService(
            CandidateRetrievalPort retrievalPort,
            SelectionStrategy selectionStrategy) {
        this.retrievalPort = Objects.requireNonNull(retrievalPort, "retrievalPort");
        this.selectionStrategy = Objects.requireNonNull(selectionStrategy, "selectionStrategy");
    }

    public PortfolioSelectionResult select(SelectionTarget target) {
        try {
            CandidateRetrievalResult retrieval = retrievalPort.retrieve(target, CANDIDATE_LIMIT);
            PortfolioSelection selection = selectionStrategy.select(target, retrieval.getCandidates());
            return new PortfolioSelectionResult(
                    retrieval.getReleaseVersion(),
                    retrieval.getRetrievalMode(),
                    selection,
                    retrieval.getCandidates(),
                    insufficiencyReason(target, selection));
        } catch (CandidateRetrievalException exception) {
            PortfolioSelection unavailable = new PortfolioSelection(
                    PortfolioSelectionStatus.TEMPORARILY_UNAVAILABLE,
                    List.of(),
                    new SelectionScore(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
                    selectionStrategy.policyVersion());
            return new PortfolioSelectionResult(
                    "UNAVAILABLE",
                    RetrievalMode.UNAVAILABLE,
                    unavailable,
                    List.of(),
                    "PUBLIC_SELECTION_UNAVAILABLE");
        }
    }

    private String insufficiencyReason(
            SelectionTarget target,
            PortfolioSelection selection) {
        if (selection.getStatus() != PortfolioSelectionStatus.INSUFFICIENT) {
            return null;
        }
        if (selection.getCandidates().size() < target.getRequestedSize()) {
            return "INSUFFICIENT_ELIGIBLE_ASSETS";
        }
        return "CAPABILITY_COVERAGE_INCOMPLETE";
    }
}
