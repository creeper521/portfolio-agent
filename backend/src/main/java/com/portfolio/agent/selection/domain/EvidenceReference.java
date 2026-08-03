package com.portfolio.agent.selection.domain;

import java.util.Objects;

public final class EvidenceReference {

    private final String claimId;
    private final String evidenceId;
    private final String label;
    private final String publicStatus;

    public EvidenceReference(String claimId, String evidenceId, String label) {
        this(claimId, evidenceId, label, "APPROVED");
    }

    public EvidenceReference(
            String claimId,
            String evidenceId,
            String label,
            String publicStatus) {
        this.claimId = requireText(claimId, "claimId");
        this.evidenceId = requireText(evidenceId, "evidenceId");
        this.label = requireText(label, "label");
        this.publicStatus = requireText(publicStatus, "publicStatus");
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

    public boolean isApproved() {
        return "APPROVED".equals(publicStatus);
    }

    public String getPublicStatus() {
        return publicStatus;
    }

    private String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
