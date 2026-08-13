package com.portfolio.agent.answer.context.service;

import com.portfolio.agent.answer.context.domain.CompletionReceipt;
import com.portfolio.agent.answer.context.domain.ContextHandle;
import com.portfolio.agent.answer.context.domain.ConversationContinuationStatus;
import com.portfolio.agent.answer.context.domain.ConversationId;
import com.portfolio.agent.answer.context.domain.RequestFingerprint;
import com.portfolio.agent.answer.context.domain.ResumeToken;
import com.portfolio.agent.answer.context.gateway.RequestReceiptStore;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.Optional;

/** Application service for UUIDv4 request idempotency and public completion receipts. */
public final class RequestReceiptService {
    private static final Duration RETRY_AFTER = Duration.ofSeconds(1);
    private final RequestReceiptStore store;

    public RequestReceiptService(RequestReceiptStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public RequestReceiptStore.ClaimResult claim(
            UUID requestToken,
            ConversationId conversationId,
            ResumeToken resumeToken,
            RequestFingerprint fingerprint,
            ContextHandle parentContextHandle,
            Instant now) {
        requireUuidV4(requestToken);
        return store.claim(requestToken, conversationId, resumeToken, fingerprint,
                parentContextHandle, now);
    }

    public CompletionReceipt complete(
            UUID requestToken,
            UUID leaseId,
            ConversationId conversationId,
            RequestFingerprint fingerprint,
            ContextHandle contextHandle,
            ConversationContinuationStatus continuationStatus,
            Instant now) {
        requireUuidV4(requestToken);
        CompletionReceipt receipt = new CompletionReceipt(
                requestToken, conversationId, fingerprint, contextHandle, continuationStatus, now);
        store.complete(requestToken, leaseId, receipt, now);
        return receipt;
    }

    public Optional<CompletionReceipt> findCompleted(UUID requestToken, Instant now) {
        requireUuidV4(requestToken);
        return store.findCompleted(requestToken, now);
    }

    public CompletionReceipt complete(
            UUID requestToken,
            ConversationId conversationId,
            RequestFingerprint fingerprint,
            ContextHandle contextHandle,
            ConversationContinuationStatus continuationStatus,
            Instant now) {
        return complete(requestToken, null, conversationId, fingerprint, contextHandle,
                continuationStatus, now);
    }

    public static void requireUuidV4(UUID requestToken) {
        Objects.requireNonNull(requestToken, "requestToken");
        if (requestToken.version() != 4 || requestToken.variant() != 2) {
            throw new IllegalArgumentException("requestToken must be UUIDv4");
        }
    }
}
