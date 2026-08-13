package com.portfolio.agent.answer.intelligence.domain;

import java.util.Objects;

public final class PortfolioRetrievedEvidenceReference {

    private final String evidenceId;
    private final String evidenceCode;
    private final String label;
    private final String evidenceType;
    private final String publicStatus;

    public PortfolioRetrievedEvidenceReference(
            String evidenceId,
            String label,
            String publicStatus) {
        this(evidenceId, evidenceId, label, "DOCUMENT", publicStatus);
    }

    public PortfolioRetrievedEvidenceReference(
            String evidenceId,
            String evidenceCode,
            String label,
            String evidenceType,
            String publicStatus) {
        this.evidenceId = requireText(evidenceId, "evidenceId");
        this.evidenceCode = requireText(evidenceCode, "evidenceCode");
        this.label = requireText(label, "label");
        this.evidenceType = requireText(evidenceType, "evidenceType");
        this.publicStatus = requireText(publicStatus, "publicStatus");
    }

    public String getEvidenceId() { return evidenceId; }
    public String getEvidenceCode() { return evidenceCode; }
    public String getLabel() { return label; }
    public String getEvidenceType() { return evidenceType; }
    public String getPublicStatus() { return publicStatus; }
    public boolean isApproved() { return "APPROVED".equals(publicStatus); }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof PortfolioRetrievedEvidenceReference that)) { return false; }
        return Objects.equals(evidenceId, that.evidenceId)
                && Objects.equals(evidenceCode, that.evidenceCode)
                && Objects.equals(label, that.label)
                && Objects.equals(evidenceType, that.evidenceType)
                && Objects.equals(publicStatus, that.publicStatus);
    }

    @Override
    public int hashCode() {
        return Objects.hash(evidenceId, evidenceCode, label, evidenceType, publicStatus);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
