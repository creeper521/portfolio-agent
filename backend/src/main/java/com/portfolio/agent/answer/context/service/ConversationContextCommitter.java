package com.portfolio.agent.answer.context.service;

import com.portfolio.agent.answer.context.domain.ContextHandle;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.service.ConversationRequestContext;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Slice-2 closed boundary. Context settlement is reintroduced from TaskArtifact by Slice 5;
 * the removed legacy Plan/Payload model is never reconstructed here.
 */
public final class ConversationContextCommitter {
    public ConversationContextCommitter(
            ConversationContextFacade facade,
            ConversationContextMutationFactory mutationFactory) {
        Objects.requireNonNull(facade, "facade");
        Objects.requireNonNull(mutationFactory, "mutationFactory");
    }

    public Map<String, ContextHandle> commit(
            ConversationAnswerResult result,
            ConversationRequestContext requestContext,
            Instant now) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(requestContext, "requestContext");
        Objects.requireNonNull(now, "now");
        return Map.of();
    }
}
