package com.portfolio.agent.answer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portfolio.agent.answer.domain.PublicSourceReferenceValue;
import com.portfolio.agent.answer.intelligence.execution.validation.PublicSourceReference;

/** Public citation DTO containing only stable reference and public relative routes. */
public final class PublicSourceReferenceResponse {
    private final String referenceKey;
    private final String label;
    private final String publishedVersion;
    private final String sourceType;
    private final String subjectRoute;
    private final String evidenceRoute;

    public PublicSourceReferenceResponse(
            String referenceKey, String sourceType, String subjectRoute, String evidenceRoute) {
        this(referenceKey, referenceKey, null, sourceType, subjectRoute, evidenceRoute);
    }

    public PublicSourceReferenceResponse(
            String referenceKey, String label, String publishedVersion,
            String sourceType, String subjectRoute, String evidenceRoute) {
        this.referenceKey = referenceKey;
        this.label = label;
        this.publishedVersion = publishedVersion;
        this.sourceType = sourceType;
        this.subjectRoute = subjectRoute;
        this.evidenceRoute = evidenceRoute;
    }

    public static PublicSourceReferenceResponse from(PublicSourceReference reference) {
        return new PublicSourceReferenceResponse(reference.getReferenceKey(),
                reference.getLabel(), reference.getPublishedVersion(), reference.getSourceType().name(),
                reference.getSubjectRoute(), reference.getEvidenceRoute());
    }

    public static PublicSourceReferenceResponse from(PublicSourceReferenceValue reference) {
        return new PublicSourceReferenceResponse(reference.getReferenceKey(), reference.getLabel(),
                reference.getPublishedVersion(), reference.getSourceType(),
                reference.getSubjectRoute(), reference.getEvidenceRoute());
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
