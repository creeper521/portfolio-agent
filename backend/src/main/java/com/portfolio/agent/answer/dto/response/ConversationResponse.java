package com.portfolio.agent.answer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portfolio.agent.answer.context.domain.ConversationContinuationStatus;

/** Public conversation continuation projection; the resume token is emitted only when explicitly supplied. */
public final class ConversationResponse {
    private final String resumeToken;
    private final ConversationContinuationStatus continuationStatus;
    private final ConversationContextSummaryResponse activeContextSummary;

    public ConversationResponse(
            String resumeToken,
            ConversationContinuationStatus continuationStatus,
            ConversationContextSummaryResponse activeContextSummary) {
        this.resumeToken = resumeToken;
        this.continuationStatus = continuationStatus;
        this.activeContextSummary = activeContextSummary;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getResumeToken() { return resumeToken; }
    public ConversationContinuationStatus getContinuationStatus() { return continuationStatus; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ConversationContextSummaryResponse getActiveContextSummary() { return activeContextSummary; }
}
