package com.portfolio.agent.answer.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public final class AnswerContextRequest {

    @NotBlank(message = "projectSlug is required")
    @Pattern(regexp = "[a-z0-9-]{1,64}", message = "projectSlug format is invalid")
    private final String projectSlug;

    @NotNull(message = "audienceRole is required")
    private final AudienceRole audienceRole;

    @NotNull(message = "focusEvidenceIds is required")
    private final List<String> focusEvidenceIds;

    @NotNull(message = "source is required")
    private final AnswerRequestSource source;

    @JsonCreator
    public AnswerContextRequest(
            @JsonProperty("projectSlug") String projectSlug,
            @JsonProperty("audienceRole") AudienceRole audienceRole,
            @JsonProperty("focusEvidenceIds") List<String> focusEvidenceIds,
            @JsonProperty("source") AnswerRequestSource source
    ) {
        this.projectSlug = projectSlug;
        this.audienceRole = audienceRole;
        this.focusEvidenceIds = focusEvidenceIds == null ? List.of() : List.copyOf(focusEvidenceIds);
        this.source = source;
    }

    public String getProjectSlug() {
        return projectSlug;
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
}
