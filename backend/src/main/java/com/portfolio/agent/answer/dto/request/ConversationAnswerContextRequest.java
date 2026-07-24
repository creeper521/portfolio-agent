package com.portfolio.agent.answer.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public final class ConversationAnswerContextRequest {

    @Pattern(regexp = "[a-z0-9-]{1,64}", message = "projectSlug format is invalid")
    private final String projectSlug;

    @Pattern(regexp = "[a-z0-9-]{1,64}", message = "caseSlug format is invalid")
    private final String caseSlug;

    @NotNull(message = "audienceRole is required")
    private final AudienceRole audienceRole;

    @NotNull(message = "source is required")
    private final AnswerRequestSource source;

    @JsonCreator
    public ConversationAnswerContextRequest(
            @JsonProperty("projectSlug") String projectSlug,
            @JsonProperty("caseSlug") String caseSlug,
            @JsonProperty("audienceRole") AudienceRole audienceRole,
            @JsonProperty("source") AnswerRequestSource source
    ) {
        this.projectSlug = projectSlug;
        this.caseSlug = caseSlug;
        this.audienceRole = audienceRole;
        this.source = source;
    }

    public String getProjectSlug() { return projectSlug; }
    public String getCaseSlug() { return caseSlug; }
    public AudienceRole getAudienceRole() { return audienceRole; }
    public AnswerRequestSource getSource() { return source; }

    @AssertTrue(message = "projectSlug and caseSlug cannot both be set")
    public boolean isSubjectHintValid() {
        return !hasText(projectSlug) || !hasText(caseSlug);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
