package com.portfolio.agent.answer.intelligence.execution.validation;

import com.portfolio.agent.answer.intelligence.execution.domain.PublicEvidenceDescriptor;

import java.util.Objects;

/** Public citation key; no database, claim, chunk, or storage identifier. */
public final class PublicSourceReference {

    private final String referenceKey;
    private final String label;
    private final String publishedVersion;
    private final PublicEvidenceDescriptor.SourceType sourceType;
    private final String subjectRoute;
    private final String evidenceRoute;

    public PublicSourceReference(
            String referenceKey, PublicEvidenceDescriptor.SourceType sourceType,
            String subjectRoute, String evidenceRoute) {
        this(referenceKey, referenceKey, null, sourceType, subjectRoute, evidenceRoute);
    }

    public PublicSourceReference(
            String referenceKey, String label, String publishedVersion,
            PublicEvidenceDescriptor.SourceType sourceType,
            String subjectRoute, String evidenceRoute) {
        this.referenceKey = requireText(referenceKey, "referenceKey");
        this.label = requireText(label, "label");
        this.publishedVersion = publishedVersion == null ? null : requireText(publishedVersion, "publishedVersion");
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
        this.subjectRoute = requireRoute(subjectRoute, "subjectRoute");
        this.evidenceRoute = requireRoute(evidenceRoute, "evidenceRoute");
    }

    public String getReferenceKey() { return referenceKey; }
    public String getLabel() { return label; }
    public String getPublishedVersion() { return publishedVersion; }
    public PublicEvidenceDescriptor.SourceType getSourceType() { return sourceType; }
    public String getSubjectRoute() { return subjectRoute; }
    public String getEvidenceRoute() { return evidenceRoute; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PublicSourceReference that)) return false;
        return referenceKey.equals(that.referenceKey) && label.equals(that.label)
                && Objects.equals(publishedVersion, that.publishedVersion) && sourceType == that.sourceType
                && subjectRoute.equals(that.subjectRoute) && evidenceRoute.equals(that.evidenceRoute);
    }

    @Override
    public int hashCode() { return Objects.hash(referenceKey, sourceType, subjectRoute, evidenceRoute); }

    @Override
    public String toString() { return "PublicSourceReference{sourceType=" + sourceType + '}'; }

    private static String requireRoute(String value, String name) {
        if (value == null || value.isBlank() || !value.startsWith("/")
                || value.startsWith("//") || value.contains(":") || value.contains("\\")
                || value.contains("..") || value.contains("\n")) {
            throw new IllegalArgumentException(name + " must be a relative public route");
        }
        return value.trim();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
