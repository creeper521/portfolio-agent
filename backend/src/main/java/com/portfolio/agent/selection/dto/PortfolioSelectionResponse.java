package com.portfolio.agent.selection.dto;

import com.portfolio.agent.selection.domain.PortfolioSelectionStatus;
import com.portfolio.agent.selection.domain.RetrievalMode;
import java.util.List;

public final class PortfolioSelectionResponse {

    private final String releaseVersion;
    private final String selectionId;
    private final String policyVersion;
    private final RetrievalMode retrievalMode;
    private final String selectionMode;
    private final PortfolioSelectionStatus status;
    private final int requestedSize;
    private final int actualSize;
    private final List<PortfolioSelectionItemResponse> items;
    private final List<CapabilityCoverageResponse> coverage;
    private final List<ComplementarityResponse> complementarity;
    private final List<PortfolioSelectionAlternativeResponse> alternatives;
    private final SelectionDegradationResponse degradation;

    public PortfolioSelectionResponse(
            String selectionId,
            String releaseVersion,
            String policyVersion,
            RetrievalMode retrievalMode,
            String selectionMode,
            PortfolioSelectionStatus status,
            int requestedSize,
            List<PortfolioSelectionItemResponse> items,
            List<CapabilityCoverageResponse> coverage,
            List<ComplementarityResponse> complementarity,
            List<PortfolioSelectionAlternativeResponse> alternatives,
            SelectionDegradationResponse degradation) {
        this.selectionId = selectionId;
        this.releaseVersion = releaseVersion;
        this.policyVersion = policyVersion;
        this.retrievalMode = retrievalMode;
        this.selectionMode = selectionMode;
        this.status = status;
        this.requestedSize = requestedSize;
        this.items = List.copyOf(items);
        this.actualSize = items.size();
        this.coverage = List.copyOf(coverage);
        this.complementarity = List.copyOf(complementarity);
        this.alternatives = List.copyOf(alternatives);
        this.degradation = degradation;
    }

    public String getSelectionId() {
        return selectionId;
    }

    public String getReleaseVersion() {
        return releaseVersion;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public RetrievalMode getRetrievalMode() {
        return retrievalMode;
    }

    public String getSelectionMode() {
        return selectionMode;
    }

    public PortfolioSelectionStatus getStatus() {
        return status;
    }

    public int getRequestedSize() {
        return requestedSize;
    }

    public int getActualSize() {
        return actualSize;
    }

    public List<PortfolioSelectionItemResponse> getItems() {
        return items;
    }

    public List<CapabilityCoverageResponse> getCoverage() {
        return coverage;
    }

    public List<ComplementarityResponse> getComplementarity() {
        return complementarity;
    }

    public List<PortfolioSelectionAlternativeResponse> getAlternatives() {
        return alternatives;
    }

    public SelectionDegradationResponse getDegradation() {
        return degradation;
    }
}
