package com.portfolio.agent.answer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

public final class PublicSourceCatalogEntryResponse {
    private final String referenceKey;
    private final String label;
    private final String publishedVersion;
    private final String sourceType;
    private final String subjectRoute;
    private final String evidenceRoute;

    public PublicSourceCatalogEntryResponse(
            String referenceKey, String label, String publishedVersion,
            String sourceType, String subjectRoute, String evidenceRoute) {
        this.referenceKey = referenceKey;
        this.label = label;
        this.publishedVersion = publishedVersion;
        this.sourceType = sourceType;
        this.subjectRoute = subjectRoute;
        this.evidenceRoute = evidenceRoute;
    }

    public String getReferenceKey() { return referenceKey; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getLabel() { return label; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getPublishedVersion() { return publishedVersion; }
    public String getSourceType() { return sourceType; }
    public String getSubjectRoute() { return subjectRoute; }
    public String getEvidenceRoute() { return evidenceRoute; }
}
