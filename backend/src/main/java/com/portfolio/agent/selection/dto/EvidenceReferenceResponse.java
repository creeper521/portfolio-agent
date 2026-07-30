package com.portfolio.agent.selection.dto;

public final class EvidenceReferenceResponse {

    private final String claimId;
    private final String evidenceId;
    private final String label;

    public EvidenceReferenceResponse(String claimId, String evidenceId, String label) {
        this.claimId = claimId;
        this.evidenceId = evidenceId;
        this.label = label;
    }

    public String getClaimId() {
        return claimId;
    }

    public String getEvidenceId() {
        return evidenceId;
    }

    public String getLabel() {
        return label;
    }
}
