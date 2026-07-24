package com.portfolio.agent.answer.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public final class ConversationSuggestedQuestion {

    private final String text;
    private final String projectSlug;
    private final String caseSlug;
    private final PortfolioKnowledgeFacet facet;

    @JsonCreator
    public ConversationSuggestedQuestion(
            @JsonProperty("text") String text,
            @JsonProperty("projectSlug") String projectSlug,
            @JsonProperty("caseSlug") String caseSlug,
            @JsonProperty("facet") PortfolioKnowledgeFacet facet
    ) {
        this.text = Objects.requireNonNull(text, "text");
        this.projectSlug = projectSlug;
        this.caseSlug = caseSlug;
        this.facet = facet;
    }

    public String getText() { return text; }
    public String getProjectSlug() { return projectSlug; }
    public String getCaseSlug() { return caseSlug; }
    public PortfolioKnowledgeFacet getFacet() { return facet; }
}
