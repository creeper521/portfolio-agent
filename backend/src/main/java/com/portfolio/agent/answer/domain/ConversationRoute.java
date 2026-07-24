package com.portfolio.agent.answer.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public final class ConversationRoute {

    private final ConversationIntent intent;
    private final ConversationAnswerScope answerScope;
    private final double confidence;
    private final String projectSlug;
    private final String caseSlug;
    private final PortfolioKnowledgeFacet facet;
    private final boolean clarificationRequired;

    @JsonCreator
    public ConversationRoute(
            @JsonProperty("intent") ConversationIntent intent,
            @JsonProperty("answerScope") ConversationAnswerScope answerScope,
            @JsonProperty("confidence") double confidence,
            @JsonProperty("projectSlug") String projectSlug,
            @JsonProperty("caseSlug") String caseSlug,
            @JsonProperty("facet") PortfolioKnowledgeFacet facet,
            @JsonProperty("clarificationRequired") boolean clarificationRequired
    ) {
        this.intent = Objects.requireNonNull(intent, "intent");
        this.answerScope = Objects.requireNonNull(answerScope, "answerScope");
        this.confidence = confidence;
        this.projectSlug = projectSlug;
        this.caseSlug = caseSlug;
        this.facet = facet == null ? PortfolioKnowledgeFacet.OVERVIEW : facet;
        this.clarificationRequired = clarificationRequired;
    }

    public ConversationIntent getIntent() { return intent; }
    public ConversationAnswerScope getAnswerScope() { return answerScope; }
    public double getConfidence() { return confidence; }
    public String getProjectSlug() { return projectSlug; }
    public String getCaseSlug() { return caseSlug; }
    public PortfolioKnowledgeFacet getFacet() { return facet; }
    public boolean isClarificationRequired() { return clarificationRequired; }
}
