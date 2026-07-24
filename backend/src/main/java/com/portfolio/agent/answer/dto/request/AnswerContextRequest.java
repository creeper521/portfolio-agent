package com.portfolio.agent.answer.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.AssertTrue;

import java.util.List;

public final class AnswerContextRequest {

    @Pattern(regexp = "[a-z0-9-]{1,64}", message = "projectSlug format is invalid")
    private final String projectSlug;

    @Pattern(regexp = "[a-z0-9-]{1,64}", message = "caseSlug format is invalid")
    private final String caseSlug;

    @NotNull(message = "audienceRole is required")
    private final AudienceRole audienceRole;

    @NotNull(message = "focusEvidenceIds is required")
    private final List<String> focusEvidenceIds;

    @NotNull(message = "source is required")
    private final AnswerRequestSource source;

    @JsonCreator
    public AnswerContextRequest(
            @JsonProperty("projectSlug") String projectSlug,
            @JsonProperty("caseSlug") String caseSlug,
            @JsonProperty("audienceRole") AudienceRole audienceRole,
            @JsonProperty("focusEvidenceIds") List<String> focusEvidenceIds,
            @JsonProperty("source") AnswerRequestSource source
    ) {
        this.projectSlug = projectSlug;
        this.caseSlug = caseSlug;
        this.audienceRole = audienceRole;
        this.focusEvidenceIds = focusEvidenceIds == null ? List.of() : List.copyOf(focusEvidenceIds);
        this.source = source;
    }

    public AnswerContextRequest(
            String projectSlug,
            AudienceRole audienceRole,
            List<String> focusEvidenceIds,
            AnswerRequestSource source
    ) {
        this(projectSlug, null, audienceRole, focusEvidenceIds, source);
    }

    public String getProjectSlug() {
        return projectSlug;
    }

    public String getCaseSlug() {
        return caseSlug;
    }

    public AudienceRole getAudienceRole() {
        return audienceRole;
    }

    public List<String> getFocusEvidenceIds() {
        return focusEvidenceIds;
    }

    public AnswerRequestSource getSource() {
        return source;
    }

    @AssertTrue(message = "exactly one of projectSlug or caseSlug is required")
    public boolean isSubjectSelectionValid() {
        return hasText(projectSlug) != hasText(caseSlug);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
