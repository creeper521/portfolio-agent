package com.portfolio.agent.answer.context.service;

import com.portfolio.agent.answer.context.codec.ConversationContextCodecRegistry;
import com.portfolio.agent.answer.context.domain.ContextHandle;
import com.portfolio.agent.answer.context.domain.ContextSlot;
import com.portfolio.agent.answer.context.domain.ConversationContextMutation;
import com.portfolio.agent.answer.context.domain.ConversationContextValue;

import java.util.Objects;

/** Creates bounded Context mutations from typed business values, never from answer text. */
public final class ConversationContextMutationFactory {
    private final ConversationContextCodecRegistry codecRegistry;
    private final ConversationContextCapacityPolicy capacityPolicy;

    public ConversationContextMutationFactory(
            ConversationContextCodecRegistry codecRegistry,
            ConversationContextCapacityPolicy capacityPolicy) {
        this.codecRegistry = Objects.requireNonNull(codecRegistry, "codecRegistry");
        this.capacityPolicy = Objects.requireNonNull(capacityPolicy, "capacityPolicy");
    }

    public ConversationContextMutation create(
            ConversationContextValue value,
            ContextHandle parentContextHandle,
            String sourceTaskId) {
        return create(value, parentContextHandle, sourceTaskId, null, null);
    }

    public ConversationContextMutation create(
            ConversationContextValue value,
            ContextHandle parentContextHandle,
            String sourceTaskId,
            ContextSlot activeSlot,
            Long expectedActiveRevision) {
        Objects.requireNonNull(value, "value");
        ConversationContextCodecRegistry.EncodedContext encoded = codecRegistry.encode(
                value.getType(), typedValue(value));
        int payloadBytes = encoded.getPayload().length;
        capacityPolicy.requirePayloadSize(payloadBytes);
        return new ConversationContextMutation(
                ContextHandle.issue(), value, parentContextHandle, sourceTaskId,
                payloadBytes, activeSlot, expectedActiveRevision);
    }

    private Object typedValue(ConversationContextValue value) {
        return switch (value.getType()) {
            case RECENT_SEMANTIC_TASK -> value.getRecentSemanticTaskContext();
            case RECOMMENDATION -> value.getRecommendationContext();
        };
    }
}
