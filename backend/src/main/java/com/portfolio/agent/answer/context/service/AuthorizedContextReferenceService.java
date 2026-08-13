package com.portfolio.agent.answer.context.service;

import com.portfolio.agent.answer.context.domain.ConversationContextEntry;
import com.portfolio.agent.answer.context.domain.ConversationContextResolution;
import com.portfolio.agent.answer.context.domain.ConversationContextType;
import com.portfolio.agent.answer.context.domain.ConversationId;
import com.portfolio.agent.answer.context.domain.RecommendationContext;
import com.portfolio.agent.answer.context.domain.ResumeToken;
import com.portfolio.agent.answer.intelligence.execution.domain.RecommendationScopeBinding;
import com.portfolio.agent.answer.routing.domain.AuthorizedContextReference;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.List;

/** Converts an opaque client reference into an executor reference only after Store authorization. */
public final class AuthorizedContextReferenceService {
    private final ConversationContextResolver resolver;

    public AuthorizedContextReferenceService(ConversationContextResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public Optional<AuthorizedContextReference> authorize(
            ConversationId conversationId,
            ResumeToken resumeToken,
            AuthorizedContextReference requestedReference,
            Instant now) {
        ConversationContextResolution resolution = resolver.resolve(
                conversationId, resumeToken, requestedReference, now);
        if (resolution.getStatus() != ConversationContextResolution.Status.RESOLVED) {
            return Optional.empty();
        }
        ConversationContextEntry entry = resolution.getEntry().orElseThrow();
        return authorizedEntry(requestedReference, entry);
    }

    public List<AuthorizedContextReference> authorizeActive(
            ConversationId conversationId,
            ResumeToken resumeToken,
            Instant now) {
        List<AuthorizedContextReference> references = new java.util.ArrayList<>();
        for (ConversationContextType type : List.of(
                ConversationContextType.RECENT_SEMANTIC_TASK,
                ConversationContextType.RECOMMENDATION)) {
            ConversationContextResolution resolution = resolver.resolve(
                    conversationId, resumeToken, null, type, now);
            if (resolution.getStatus() != ConversationContextResolution.Status.RESOLVED) {
                continue;
            }
            ConversationContextEntry entry = resolution.getEntry().orElseThrow();
            AuthorizedContextReference requested = new AuthorizedContextReference(
                    entry.getContextHandle().asBase64Url(), type.name());
            authorizedEntry(requested, entry).ifPresent(references::add);
        }
        return List.copyOf(references);
    }

    private Optional<AuthorizedContextReference> authorizedEntry(
            AuthorizedContextReference requestedReference,
            ConversationContextEntry entry) {
        if (!entry.getContextType().name().equals(requestedReference.getExpectedContextType())) {
            return Optional.empty();
        }
        if (entry.getContextType() == ConversationContextType.RECOMMENDATION) {
            RecommendationContext recommendation = entry.getValue().getRecommendationContext();
            return Optional.of(new AuthorizedContextReference(
                    requestedReference.getContextHandle(), requestedReference.getExpectedContextType(),
                    new RecommendationScopeBinding(
                            recommendation.getAuthorizedScope(),
                            recommendation.getAuthorizedScope().getContentVersion())));
        }
        return Optional.of(new AuthorizedContextReference(
                requestedReference.getContextHandle(), requestedReference.getExpectedContextType()));
    }
}
