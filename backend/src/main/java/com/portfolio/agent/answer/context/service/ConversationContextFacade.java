package com.portfolio.agent.answer.context.service;

import com.portfolio.agent.answer.context.domain.ContextHandle;
import com.portfolio.agent.answer.context.domain.ContextSlot;
import com.portfolio.agent.answer.context.domain.ConversationContextMutation;
import com.portfolio.agent.answer.context.domain.ConversationContextResolution;
import com.portfolio.agent.answer.context.domain.ConversationContextSummary;
import com.portfolio.agent.answer.context.domain.ConversationId;
import com.portfolio.agent.answer.context.domain.ResumeToken;
import com.portfolio.agent.answer.context.gateway.ConversationBusinessContextStore;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Application seam for typed Context persistence, resolution and safe summaries. */
public final class ConversationContextFacade {
    private final ConversationBusinessContextStore store;
    private final ConversationContextResolver resolver;
    private final SafeContextSummaryProjector summaryProjector;

    public ConversationContextFacade(
            ConversationBusinessContextStore store,
            ConversationContextResolver resolver,
            SafeContextSummaryProjector summaryProjector) {
        this.store = Objects.requireNonNull(store, "store");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.summaryProjector = Objects.requireNonNull(summaryProjector, "summaryProjector");
    }

    public ConversationBusinessContextStore.SaveResult save(
            ConversationId conversationId,
            ResumeToken resumeToken,
            ConversationContextMutation mutation,
            Instant now) {
        return store.save(conversationId, resumeToken, mutation, now);
    }

    public ConversationContextResolution resolve(
            ConversationId conversationId,
            ResumeToken resumeToken,
            ContextHandle explicitHandle,
            ConversationContextLookupCriteria criteria,
            Instant now) {
        return resolver.resolve(conversationId, resumeToken, explicitHandle, criteria, now);
    }

    public Optional<ConversationBusinessContextStore.ActiveContext> active(
            ConversationId conversationId,
            ResumeToken resumeToken,
            ContextSlot slot,
            Instant now) {
        return store.active(conversationId, resumeToken, slot, now);
    }

    public Optional<ConversationContextSummary> summary(
            ConversationId conversationId,
            ResumeToken resumeToken,
            ContextHandle contextHandle,
            Instant now) {
        return store.resolve(conversationId, resumeToken, contextHandle, now)
                .map(summaryProjector::project);
    }

    public Optional<ConversationContextSummary> summary(
            ResumeToken resumeToken, Instant now) {
        return store.findConversation(resumeToken)
                .flatMap(conversationId -> store.list(conversationId, resumeToken, now).stream().findFirst())
                .map(summaryProjector::project);
    }

    public void clear(ConversationId conversationId, ResumeToken resumeToken) {
        store.clear(conversationId, resumeToken);
    }

    public void clear(ResumeToken resumeToken) {
        store.clear(resumeToken);
    }
}
