package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.context.domain.CompletionReceipt;
import com.portfolio.agent.answer.context.domain.ContextHandle;
import com.portfolio.agent.answer.context.domain.ConversationContinuationStatus;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;

import java.util.Objects;
import java.util.Optional;
import java.util.Map;

/** One production request outcome: either a fresh answer or a persisted completion receipt. */
public final class ProductionConversationExecution {
    private final ConversationAnswerResult answer;
    private final CompletionReceipt completionReceipt;
    private final Map<String, ContextHandle> contextHandles;
    private final ConversationContinuationStatus continuationStatus;

    private ProductionConversationExecution(
            ConversationAnswerResult answer, CompletionReceipt completionReceipt) {
        this(answer, completionReceipt, Map.of(), completionReceipt == null
                ? ConversationContinuationStatus.AVAILABLE
                : completionReceipt.getContinuationStatus());
    }

    private ProductionConversationExecution(
            ConversationAnswerResult answer,
            CompletionReceipt completionReceipt,
            Map<String, ContextHandle> contextHandles,
            ConversationContinuationStatus continuationStatus) {
        this.answer = answer;
        this.completionReceipt = completionReceipt;
        this.contextHandles = Map.copyOf(Objects.requireNonNull(contextHandles, "contextHandles"));
        this.continuationStatus = Objects.requireNonNull(continuationStatus, "continuationStatus");
        if ((answer == null) == (completionReceipt == null)) {
            throw new IllegalArgumentException("execution must contain exactly one outcome");
        }
    }

    public static ProductionConversationExecution answer(ConversationAnswerResult answer) {
        return new ProductionConversationExecution(Objects.requireNonNull(answer, "answer"), null);
    }

    public static ProductionConversationExecution answer(
            ConversationAnswerResult answer, Map<String, ContextHandle> contextHandles) {
        return new ProductionConversationExecution(
                Objects.requireNonNull(answer, "answer"), null, contextHandles,
                contextHandles.isEmpty()
                        ? ConversationContinuationStatus.NOT_APPLICABLE
                        : ConversationContinuationStatus.AVAILABLE);
    }

    public static ProductionConversationExecution answer(
            ConversationAnswerResult answer,
            Map<String, ContextHandle> contextHandles,
            ConversationContinuationStatus continuationStatus) {
        return new ProductionConversationExecution(
                Objects.requireNonNull(answer, "answer"), null, contextHandles, continuationStatus);
    }

    public static ProductionConversationExecution receipt(CompletionReceipt receipt) {
        return new ProductionConversationExecution(null, Objects.requireNonNull(receipt, "receipt"));
    }

    public Optional<ConversationAnswerResult> getAnswer() {
        return Optional.ofNullable(answer);
    }

    public Optional<CompletionReceipt> getCompletionReceipt() {
        return Optional.ofNullable(completionReceipt);
    }

    public Map<String, ContextHandle> getContextHandles() { return contextHandles; }
    public ConversationContinuationStatus getContinuationStatus() { return continuationStatus; }
}
