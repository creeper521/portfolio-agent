package com.portfolio.agent.answer.context.service;

import com.portfolio.agent.answer.context.domain.ContextHandle;
import com.portfolio.agent.answer.context.domain.ContextSlot;
import com.portfolio.agent.answer.context.domain.ConversationContextEntry;
import com.portfolio.agent.answer.context.domain.ConversationContextResolution;
import com.portfolio.agent.answer.context.domain.ConversationContextType;
import com.portfolio.agent.answer.context.domain.ConversationId;
import com.portfolio.agent.answer.context.domain.ResumeToken;
import com.portfolio.agent.answer.context.gateway.ConversationBusinessContextStore;
import com.portfolio.agent.answer.routing.domain.AuthorizedContextReference;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Fixed Context resolution order: explicit handle, unique Active, latest Active, clarification. */
public final class ConversationContextResolver {
    private final ConversationBusinessContextStore store;

    public ConversationContextResolver(ConversationBusinessContextStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public ConversationContextResolution resolve(
            ConversationId conversationId,
            ResumeToken resumeToken,
            ContextHandle explicitHandle,
            ConversationContextLookupCriteria criteria,
            Instant now) {
        Objects.requireNonNull(criteria, "criteria");
        if (explicitHandle != null) {
            ConversationBusinessContextStore.LookupResult lookup;
            try {
                lookup = store.lookup(conversationId, resumeToken, explicitHandle, now);
            } catch (RuntimeException exception) {
                return ConversationContextResolution.unavailable();
            }
            if (lookup.getStatus() == ConversationBusinessContextStore.LookupResult.Status.EXPIRED) {
                return ConversationContextResolution.expired();
            }
            if (lookup.getStatus() == ConversationBusinessContextStore.LookupResult.Status.NOT_FOUND) {
                return ConversationContextResolution.invalidReference();
            }
            Optional<ConversationContextEntry> explicit = lookup.getEntry();
            if (!matches(explicit.orElseThrow(), criteria)) {
                return ConversationContextResolution.incompatible();
            }
            return ConversationContextResolution.resolved(
                    ConversationContextResolution.SelectionReason.EXPLICIT_HANDLE,
                    explicit.orElseThrow());
        }

        List<ConversationContextEntry> active = activeCompatible(
                conversationId, resumeToken, criteria, now);
        if (active.size() == 1) {
            return ConversationContextResolution.resolved(
                    ConversationContextResolution.SelectionReason.UNIQUE_ACTIVE, active.get(0));
        }
        if (active.size() > 1) {
            active.sort(Comparator.comparing(ConversationContextEntry::getCreatedAt).reversed());
            if (!active.get(0).getCreatedAt().equals(active.get(1).getCreatedAt())) {
                return ConversationContextResolution.resolved(
                        ConversationContextResolution.SelectionReason.MOST_RECENT_ACTIVE, active.get(0));
            }
        }
        return ConversationContextResolution.clarificationRequired();
    }

    public ConversationContextResolution resolve(
            ConversationId conversationId,
            ResumeToken resumeToken,
            ContextHandle explicitHandle,
            ConversationContextType contextType,
            Instant now) {
        return resolve(conversationId, resumeToken, explicitHandle,
                new ConversationContextLookupCriteria(contextType, null), now);
    }

    public ConversationContextResolution resolve(
            ConversationId conversationId,
            ResumeToken resumeToken,
            ContextHandle explicitHandle,
            ConversationContextType contextType,
            SemanticRoutingTypes.SemanticTaskType taskType,
            Instant now) {
        return resolve(conversationId, resumeToken, explicitHandle,
                new ConversationContextLookupCriteria(contextType, taskType), now);
    }

    public ConversationContextResolution resolve(
            ConversationId conversationId,
            ResumeToken resumeToken,
            AuthorizedContextReference reference,
            Instant now) {
        try {
            ContextHandle handle = ContextHandle.fromBase64Url(reference.getContextHandle());
            ConversationContextType type = ConversationContextType.valueOf(
                    reference.getExpectedContextType());
            return resolve(conversationId, resumeToken, handle, type, now);
        } catch (RuntimeException exception) {
            return ConversationContextResolution.invalidReference();
        }
    }

    private List<ConversationContextEntry> activeCompatible(
            ConversationId conversationId,
            ResumeToken resumeToken,
            ConversationContextLookupCriteria criteria,
            Instant now) {
        List<ConversationContextEntry> entries = store.list(conversationId, resumeToken, now);
        List<ConversationContextEntry> result = new ArrayList<>();
        for (ContextSlot slot : ContextSlot.values()) {
            if (slot.contextType() != criteria.getContextType()) {
                continue;
            }
            store.active(conversationId, resumeToken, slot, now)
                    .flatMap(active -> entries.stream()
                            .filter(entry -> entry.getContextHandle().equals(active.getContextHandle()))
                            .findFirst())
                    .filter(entry -> matches(entry, criteria))
                    .ifPresent(result::add);
        }
        return result;
    }

    private boolean matches(
            ConversationContextEntry entry, ConversationContextLookupCriteria criteria) {
        if (entry.getContextType() != criteria.getContextType()) {
            return false;
        }
        return criteria.getTaskType() == null
                || entry.getValue().getRecentSemanticTaskContext().getTaskType()
                == criteria.getTaskType();
    }
}
