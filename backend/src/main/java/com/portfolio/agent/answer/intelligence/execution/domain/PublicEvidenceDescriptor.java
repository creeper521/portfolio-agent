package com.portfolio.agent.answer.intelligence.execution.domain;

import java.time.LocalDate;
import java.util.Objects;

/** Public, non-sensitive evidence metadata crossing the capability boundary. */
public final class PublicEvidenceDescriptor {

    public enum SourceType {
        COLLECTION,
        DOCUMENT,
        SCREENSHOT,
        CODE,
        TEST_RESULT
    }

    private final String evidenceId;
    private final String evidenceCode;
    private final String label;
    private final String contentVersion;
    private final String publicStatus;
    private final SourceType sourceType;
    private final String subjectRoute;
    private final String evidenceRoute;
    private final LocalDate validUntil;

    public PublicEvidenceDescriptor(
            String evidenceCode, String contentVersion, String publicStatus,
            SourceType sourceType, String subjectRoute, String evidenceRoute,
            LocalDate validUntil) {
        this(evidenceCode, evidenceCode, evidenceCode, contentVersion, publicStatus,
                sourceType, subjectRoute, evidenceRoute, validUntil);
    }

    public PublicEvidenceDescriptor(
            String evidenceId, String evidenceCode, String label,
            String contentVersion, String publicStatus,
            SourceType sourceType, String subjectRoute, String evidenceRoute,
            LocalDate validUntil) {
        this.evidenceId = requireText(evidenceId, "evidenceId");
        this.evidenceCode = requireText(evidenceCode, "evidenceCode");
        this.label = requireText(label, "label");
        this.contentVersion = requireText(contentVersion, "contentVersion");
        this.publicStatus = requireText(publicStatus, "publicStatus");
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
        this.subjectRoute = publicRoute(subjectRoute, "subjectRoute");
        this.evidenceRoute = publicRoute(evidenceRoute, "evidenceRoute");
        this.validUntil = validUntil;
    }

    public String getEvidenceId() { return evidenceId; }
    public String getEvidenceCode() { return evidenceCode; }
    public String getLabel() { return label; }
    public String getContentVersion() { return contentVersion; }
    public String getPublicStatus() { return publicStatus; }
    public SourceType getSourceType() { return sourceType; }
    public String getSubjectRoute() { return subjectRoute; }
    public String getEvidenceRoute() { return evidenceRoute; }
    public LocalDate getValidUntil() { return validUntil; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PublicEvidenceDescriptor that)) return false;
        return evidenceId.equals(that.evidenceId) && evidenceCode.equals(that.evidenceCode)
                && label.equals(that.label) && contentVersion.equals(that.contentVersion)
                && publicStatus.equals(that.publicStatus) && sourceType == that.sourceType
                && subjectRoute.equals(that.subjectRoute) && evidenceRoute.equals(that.evidenceRoute)
                && Objects.equals(validUntil, that.validUntil);
    }

    @Override
    public int hashCode() {
        return Objects.hash(evidenceId, evidenceCode, label, contentVersion, publicStatus, sourceType,
                subjectRoute, evidenceRoute, validUntil);
    }

    @Override
    public String toString() {
        return "PublicEvidenceDescriptor{sourceType=" + sourceType + '}';
    }

    private static String publicRoute(String value, String name) {
        String normalized = requireText(value, name);
        if (!normalized.startsWith("/") || normalized.startsWith("//")
                || normalized.contains(":") || normalized.contains("\\")
                || normalized.contains("..") || normalized.contains("\n")) {
            throw new IllegalArgumentException(name + " must be a relative public route");
        }
        return normalized;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
