package com.portfolio.agent.answer.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.portfolio.agent.answer.domain.ConversationTopic;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;

public final class ConversationAnswerContextRequest {

    @Pattern(regexp = "[a-z0-9-]{1,64}", message = "projectSlug format is invalid")
    private final String projectSlug;

    @Pattern(regexp = "[a-z0-9-]{1,64}", message = "caseSlug format is invalid")
    private final String caseSlug;

    @NotNull(message = "audienceRole is required")
    private final AudienceRole audienceRole;

    @NotNull(message = "source is required")
    private final AnswerRequestSource source;

    @Size(max = 7, message = "coveredTopics must contain at most 7 items")
    private final List<ConversationTopic> coveredTopics;

    @JsonCreator
    public ConversationAnswerContextRequest(
            @JsonProperty("projectSlug") String projectSlug,
            @JsonProperty("caseSlug") String caseSlug,
            @JsonProperty("audienceRole") AudienceRole audienceRole,
            @JsonProperty("source") AnswerRequestSource source,
            @JsonProperty("coveredTopics") List<ConversationTopic> coveredTopics
    ) {
        this.projectSlug = projectSlug;
        this.caseSlug = caseSlug;
        this.audienceRole = audienceRole;
        this.source = source;
        this.coveredTopics = coveredTopics == null
                ? List.of()
                : List.copyOf(coveredTopics);
    }

    public ConversationAnswerContextRequest(
            String projectSlug,
            String caseSlug,
            AudienceRole audienceRole,
            AnswerRequestSource source
    ) {
        this(projectSlug, caseSlug, audienceRole, source, List.of());
    }

    public String getProjectSlug() { return projectSlug; }
    public String getCaseSlug() { return caseSlug; }
    public AudienceRole getAudienceRole() { return audienceRole; }
    public AnswerRequestSource getSource() { return source; }
    public List<ConversationTopic> getCoveredTopics() { return coveredTopics; }

    @AssertTrue(message = "projectSlug and caseSlug cannot both be set")
    public boolean isSubjectHintValid() {
        return !hasText(projectSlug) || !hasText(caseSlug);
    }

    @AssertTrue(message = "coveredTopics must not contain duplicates")
    public boolean isCoveredTopicsDistinct() {
        return new HashSet<>(coveredTopics).size() == coveredTopics.size();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
