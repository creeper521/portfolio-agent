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
        return authorizeDetailed(conversationId, resumeToken, requestedReference, now).getReference();
    }

    public AuthorizedContextReferenceResult authorizeDetailed(
            ConversationId conversationId,
            ResumeToken resumeToken,
            AuthorizedContextReference requestedReference,
            Instant now) {
        ConversationContextResolution resolution = resolver.resolve(
                conversationId, resumeToken, requestedReference, now);
        if (resolution.getStatus() != ConversationContextResolution.Status.RESOLVED) {
            return new AuthorizedContextReferenceResult(resolution, null);
        }
        ConversationContextEntry entry = resolution.getEntry().orElseThrow();
        Optional<AuthorizedContextReference> authorized = authorizedEntry(requestedReference, entry);
        if (authorized.isEmpty()) {
            return new AuthorizedContextReferenceResult(
                    ConversationContextResolution.incompatible(), null);
        }
        return new AuthorizedContextReferenceResult(resolution, authorized.orElseThrow());
    }

    public AuthorizedContextReferenceResult authorizeDetailed(
            ConversationId conversationId,
            ResumeToken resumeToken,
            AuthorizedContextReference requestedReference,
            Instant now,
            String currentContentVersion) {
        ConversationContextResolution resolution = resolver.resolve(
                conversationId, resumeToken, requestedReference, now);
        if (resolution.getStatus() != ConversationContextResolution.Status.RESOLVED) {
            return new AuthorizedContextReferenceResult(resolution, null);
        }
        ConversationContextEntry entry = resolution.getEntry().orElseThrow();
        ContextVersionPolicy policy = versionPolicy(entry, requestedReference);
        ContextVersionDecision decision = ContextVersionDecision.evaluate(
                policy, storedContentVersion(entry), currentContentVersion);
        if (decision.isStale() || decision.getStatus() == ContextVersionStatus.SOURCE_CHANGED) {
            return new AuthorizedContextReferenceResult(
                    ConversationContextResolution.incompatible(), null, decision);
        }
        Optional<AuthorizedContextReference> authorized = authorizedEntry(
                requestedReference, entry, currentContentVersion);
        if (authorized.isEmpty()) {
            return new AuthorizedContextReferenceResult(
                    ConversationContextResolution.incompatible(), null,
                    ContextVersionDecision.subjectUnavailable(policy));
        }
        return new AuthorizedContextReferenceResult(resolution, authorized.orElseThrow(), decision);
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
        return authorizedEntry(requestedReference, entry, null);
    }

    private Optional<AuthorizedContextReference> authorizedEntry(
            AuthorizedContextReference requestedReference,
            ConversationContextEntry entry,
            String currentContentVersion) {
        if (!entry.getContextType().name().equals(requestedReference.getExpectedContextType())) {
            return Optional.empty();
        }
        if (entry.getContextType() == ConversationContextType.RECOMMENDATION) {
            RecommendationContext recommendation = entry.getValue().getRecommendationContext();
            com.portfolio.agent.answer.context.domain.OrderedResultSelection.Item selected = null;
            if (requestedReference.getResultItemId().isPresent()) {
                selected = recommendation.getSelectedResults() == null ? null
                        : recommendation.getSelectedResults().getItems().stream()
                        .filter(item -> requestedReference.getResultItemId().orElseThrow()
                                .equals(item.getResultItemId())).findFirst().orElse(null);
                if (selected == null) {
                    return Optional.empty();
                }
            }
            com.portfolio.agent.answer.routing.domain.SubjectReference selectedSubject = selected == null
                    ? null : new com.portfolio.agent.answer.routing.domain.SubjectReference(
                            selected.getSubjectType(), selected.getPortfolioId(),
                            com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectResolutionSource.STRUCTURED_RESULT,
                            currentContentVersion == null
                                    ? recommendation.getAuthorizedScope().getContentVersion()
                                    : currentContentVersion);
            com.portfolio.agent.answer.intelligence.execution.domain.AuthorizedSubjectScope scope =
                    selectedSubject == null ? recommendation.getAuthorizedScope()
                            : com.portfolio.agent.answer.intelligence.execution.domain.AuthorizedSubjectScope
                            .exactSubjects(List.of(selectedSubject), selectedSubject.getContentVersion());
            return Optional.of(new AuthorizedContextReference(
                    requestedReference.getContextHandle(), requestedReference.getExpectedContextType(),
                    new RecommendationScopeBinding(
                            scope, scope.getContentVersion()),
                    requestedReference.getResultItemId().orElse(null), selectedSubject));
        }
        return Optional.of(new AuthorizedContextReference(
                requestedReference.getContextHandle(), requestedReference.getExpectedContextType()));
    }

    private ContextVersionPolicy versionPolicy(
            ConversationContextEntry entry, AuthorizedContextReference requestedReference) {
        if (entry.getContextType() == ConversationContextType.RECENT_SEMANTIC_TASK) {
            return ContextVersionPolicy.LATEST_REVALIDATED;
        }
        return requestedReference.getResultItemId().isPresent()
                ? ContextVersionPolicy.SNAPSHOT_SELECT_THEN_LATEST
                : ContextVersionPolicy.SNAPSHOT_STRICT;
    }

    private String storedContentVersion(ConversationContextEntry entry) {
        return entry.getContextType() == ConversationContextType.RECOMMENDATION
                ? entry.getValue().getRecommendationContext().getAuthorizedScope().getContentVersion()
                : entry.getValue().getRecentSemanticTaskContext().getContentVersion();
    }
}
