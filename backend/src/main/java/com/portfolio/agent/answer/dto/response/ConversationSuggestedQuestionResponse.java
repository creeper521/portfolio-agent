package com.portfolio.agent.answer.dto.response;

import com.portfolio.agent.answer.domain.ConversationSuggestedQuestion;
import com.portfolio.agent.answer.domain.PortfolioKnowledgeFacet;

public final class ConversationSuggestedQuestionResponse {

    private final String text;
    private final String projectSlug;
    private final String caseSlug;
    private final PortfolioKnowledgeFacet facet;

    public ConversationSuggestedQuestionResponse(
            String text,
            String projectSlug,
            String caseSlug,
            PortfolioKnowledgeFacet facet
    ) {
        this.text = text;
        this.projectSlug = projectSlug;
        this.caseSlug = caseSlug;
        this.facet = facet;
    }

    public static ConversationSuggestedQuestionResponse from(
            ConversationSuggestedQuestion question
    ) {
        return new ConversationSuggestedQuestionResponse(
                question.getText(),
                question.getProjectSlug(),
                question.getCaseSlug(),
                question.getFacet());
    }

    public String getText() { return text; }
    public String getProjectSlug() { return projectSlug; }
    public String getCaseSlug() { return caseSlug; }
    public PortfolioKnowledgeFacet getFacet() { return facet; }
}
