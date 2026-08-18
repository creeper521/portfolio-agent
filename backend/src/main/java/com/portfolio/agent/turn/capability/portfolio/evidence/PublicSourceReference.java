package com.portfolio.agent.turn.capability.portfolio.evidence;

import java.util.Objects;

public final class PublicSourceReference {
    private final String referenceKey;
    private final String label;
    private final String publishedVersion;
    private final String sourceType;
    private final String subjectRoute;
    private final String evidenceRoute;

    public PublicSourceReference(
            String referenceKey, String label, String publishedVersion,
            String sourceType, String subjectRoute, String evidenceRoute) {
        this.referenceKey = text(referenceKey, "referenceKey");
        this.label = text(label, "label");
        this.publishedVersion = text(publishedVersion, "publishedVersion");
        this.sourceType = text(sourceType, "sourceType");
        this.subjectRoute = route(subjectRoute, "subjectRoute");
        this.evidenceRoute = route(evidenceRoute, "evidenceRoute");
    }
    public String getReferenceKey() { return referenceKey; }
    public String getLabel() { return label; }
    public String getPublishedVersion() { return publishedVersion; }
    public String getSourceType() { return sourceType; }
    public String getSubjectRoute() { return subjectRoute; }
    public String getEvidenceRoute() { return evidenceRoute; }
    private static String text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
    private static String route(String value, String name) {
        String route = text(value, name);
        if (!route.startsWith("/") || route.startsWith("//") || route.contains(":")
                || route.contains("\\") || route.contains("..")) {
            throw new IllegalArgumentException(name + " must be a public relative route");
        }
        return route;
    }
    @Override public boolean equals(Object other) {
        return other instanceof PublicSourceReference that
                && referenceKey.equals(that.referenceKey)
                && label.equals(that.label)
                && publishedVersion.equals(that.publishedVersion)
                && sourceType.equals(that.sourceType)
                && subjectRoute.equals(that.subjectRoute)
                && evidenceRoute.equals(that.evidenceRoute);
    }
    @Override public int hashCode() {
        return Objects.hash(referenceKey, label, publishedVersion, sourceType, subjectRoute, evidenceRoute);
    }
}
