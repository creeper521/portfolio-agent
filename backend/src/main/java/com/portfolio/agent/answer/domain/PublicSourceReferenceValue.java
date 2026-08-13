package com.portfolio.agent.answer.domain;

import java.util.Objects;

/** Stable public citation data carried across the P3-to-response boundary. */
public final class PublicSourceReferenceValue {
    private final String referenceKey;
    private final String label;
    private final String publishedVersion;
    private final String sourceType;
    private final String subjectRoute;
    private final String evidenceRoute;

    public PublicSourceReferenceValue(
            String referenceKey, String sourceType, String subjectRoute, String evidenceRoute) {
        this(referenceKey, referenceKey, null, sourceType, subjectRoute, evidenceRoute);
    }

    public PublicSourceReferenceValue(
            String referenceKey, String label, String publishedVersion,
            String sourceType, String subjectRoute, String evidenceRoute) {
        this.referenceKey = requireText(referenceKey, "referenceKey");
        this.label = requireText(label, "label");
        this.publishedVersion = publishedVersion == null ? null : requireText(publishedVersion, "publishedVersion");
        this.sourceType = requireText(sourceType, "sourceType");
        this.subjectRoute = requireRoute(subjectRoute, "subjectRoute");
        this.evidenceRoute = requireRoute(evidenceRoute, "evidenceRoute");
    }

    public String getReferenceKey() { return referenceKey; }
    public String getLabel() { return label; }
    public String getPublishedVersion() { return publishedVersion; }
    public String getSourceType() { return sourceType; }
    public String getSubjectRoute() { return subjectRoute; }
    public String getEvidenceRoute() { return evidenceRoute; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PublicSourceReferenceValue that)) return false;
        return referenceKey.equals(that.referenceKey) && label.equals(that.label)
                && Objects.equals(publishedVersion, that.publishedVersion) && sourceType.equals(that.sourceType)
                && subjectRoute.equals(that.subjectRoute) && evidenceRoute.equals(that.evidenceRoute);
    }
    @Override public int hashCode() { return Objects.hash(referenceKey, sourceType, subjectRoute, evidenceRoute); }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
    private static String requireRoute(String value, String name) {
        String normalized = requireText(value, name);
        if (!normalized.startsWith("/") || normalized.startsWith("//") || normalized.contains(":")
                || normalized.contains("\\") || normalized.contains("..") || normalized.contains("\n")) {
            throw new IllegalArgumentException(name + " must be a relative public route");
        }
        return normalized;
    }
}
