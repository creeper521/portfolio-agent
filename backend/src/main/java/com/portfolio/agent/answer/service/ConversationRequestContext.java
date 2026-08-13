package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.context.domain.ConversationId;
import com.portfolio.agent.answer.context.domain.ResumeToken;
import com.portfolio.agent.answer.routing.domain.AuthorizedContextReference;

import java.util.Objects;
import java.util.Optional;

/** Request-scoped typed context; servlet headers are converted before entering the runtime. */
public final class ConversationRequestContext {
    private final ConversationId conversationId;
    private final ResumeToken resumeToken;
    private final AuthorizedContextReference contextReference;
    private final boolean newConversation;

    public ConversationRequestContext(
            ConversationId conversationId,
            ResumeToken resumeToken,
            AuthorizedContextReference contextReference) {
        this(conversationId, resumeToken, contextReference, false);
    }

    public ConversationRequestContext(
            ConversationId conversationId,
            ResumeToken resumeToken,
            AuthorizedContextReference contextReference,
            boolean newConversation) {
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId");
        this.resumeToken = Objects.requireNonNull(resumeToken, "resumeToken");
        this.contextReference = contextReference;
        this.newConversation = newConversation;
    }

    public ConversationId getConversationId() { return conversationId; }
    public ResumeToken getResumeToken() { return resumeToken; }
    public Optional<AuthorizedContextReference> getContextReference() {
        return Optional.ofNullable(contextReference);
    }
    public boolean isNewConversation() { return newConversation; }

    @Override
    public String toString() {
        return "ConversationRequestContext{hasResumeToken=true, newConversation=" + newConversation
                + ", hasContextReference=" + (contextReference != null) + '}';
    }
}
