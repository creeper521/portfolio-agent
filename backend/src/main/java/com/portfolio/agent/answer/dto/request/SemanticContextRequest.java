package com.portfolio.agent.answer.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Structured context for semantic routing; it deliberately carries no answer text. */
public final class SemanticContextRequest {

    @Valid
    @Size(max = 6, message = "activeSubjects must contain at most 6 values")
    private final List<SubjectReferenceRequest> activeSubjects;

    @Valid
    @Size(max = 6, message = "resultReferences must contain at most 6 values")
    private final List<ResultReferenceRequest> resultReferences;

    @Valid
    private final PendingPlanReferenceRequest pendingPlanReference;

    @Pattern(regexp = "INTERVIEWER|MENTOR|HR|GUEST", message = "audienceRole is invalid")
    private final String audienceRole;

    @Size(max = 100, message = "requestSource must not exceed 100 characters")
    private final String requestSource;

    @Size(max = 20, message = "coveredTopics must contain at most 20 values")
    private final List<String> coveredTopics;

    @JsonCreator
    public SemanticContextRequest(
            @JsonProperty("activeSubjects") List<SubjectReferenceRequest> activeSubjects,
            @JsonProperty("resultReferences") List<ResultReferenceRequest> resultReferences,
            @JsonProperty("pendingPlanReference") PendingPlanReferenceRequest pendingPlanReference,
            @JsonProperty("audienceRole") String audienceRole,
            @JsonProperty("requestSource") String requestSource,
            @JsonProperty("coveredTopics") List<String> coveredTopics) {
        this.activeSubjects = activeSubjects == null ? List.of() : List.copyOf(activeSubjects);
        this.resultReferences = resultReferences == null ? List.of() : List.copyOf(resultReferences);
        this.pendingPlanReference = pendingPlanReference;
        this.audienceRole = audienceRole;
        this.requestSource = requestSource;
        this.coveredTopics = coveredTopics == null ? List.of() : List.copyOf(coveredTopics);
    }

    public SemanticContextRequest(
            List<SubjectReferenceRequest> activeSubjects,
            List<?> resultReferences,
            String audienceRole) {
        this(activeSubjects, coerceResultReferences(resultReferences), null, audienceRole, null, List.of());
    }

    public List<SubjectReferenceRequest> getActiveSubjects() { return activeSubjects; }
    public List<ResultReferenceRequest> getResultReferences() { return resultReferences; }
    public PendingPlanReferenceRequest getPendingPlanReference() { return pendingPlanReference; }
    public String getAudienceRole() { return audienceRole; }
    public String getRequestSource() { return requestSource; }
    public List<String> getCoveredTopics() { return coveredTopics; }

    public static final class SubjectReferenceRequest {

        @NotBlank(message = "subjectType is required")
        @Pattern(regexp = "PROJECT|CASE|RESULT", message = "subjectType is invalid")
        private final String subjectType;

        @NotBlank(message = "subjectId is required")
        @Size(max = 100, message = "subjectId must not exceed 100 characters")
        private final String subjectId;

        @JsonCreator
        public SubjectReferenceRequest(
                @JsonProperty("subjectType") String subjectType,
                @JsonProperty("subjectId") String subjectId) {
            this.subjectType = subjectType;
            this.subjectId = subjectId;
        }

        public String getSubjectType() { return subjectType; }
        public String getSubjectId() { return subjectId; }
    }

    public static final class ResultReferenceRequest {
        @NotBlank(message = "referenceType is required")
        @Size(max = 50, message = "referenceType must not exceed 50 characters")
        private final String referenceType;

        @NotBlank(message = "referenceId is required")
        @Size(max = 100, message = "referenceId must not exceed 100 characters")
        private final String referenceId;

        @JsonCreator
        public ResultReferenceRequest(
                @JsonProperty("referenceType") String referenceType,
                @JsonProperty("referenceId") String referenceId) {
            this.referenceType = referenceType;
            this.referenceId = referenceId;
        }

        public String getReferenceType() { return referenceType; }
        public String getReferenceId() { return referenceId; }
    }

    public static final class PendingPlanReferenceRequest {
        @NotBlank(message = "planId is required")
        @Size(max = 100, message = "planId must not exceed 100 characters")
        private final String planId;

        @NotBlank(message = "planFingerprint is required")
        @Size(max = 200, message = "planFingerprint must not exceed 200 characters")
        private final String planFingerprint;

        @JsonCreator
        public PendingPlanReferenceRequest(
                @JsonProperty("planId") String planId,
                @JsonProperty("planFingerprint") String planFingerprint) {
            this.planId = planId;
            this.planFingerprint = planFingerprint;
        }

        public String getPlanId() { return planId; }
        public String getPlanFingerprint() { return planFingerprint; }
    }

    private static List<ResultReferenceRequest> coerceResultReferences(List<?> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(value -> {
            if (value instanceof ResultReferenceRequest reference) {
                return reference;
            }
            if (value instanceof SubjectReferenceRequest subject) {
                return new ResultReferenceRequest(subject.getSubjectType(), subject.getSubjectId());
            }
            throw new IllegalArgumentException("resultReferences contains unsupported value");
        }).toList();
    }
}
