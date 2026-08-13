package com.portfolio.agent.answer.context.adapter.memory;

import com.portfolio.agent.answer.context.domain.CompletionReceipt;
import com.portfolio.agent.answer.context.domain.ContextHandle;
import com.portfolio.agent.answer.context.domain.ConversationId;
import com.portfolio.agent.answer.context.domain.RequestFingerprint;
import com.portfolio.agent.answer.context.domain.ResumeToken;
import com.portfolio.agent.answer.context.gateway.RequestReceiptStore;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Explicit test/local receipt store with a 30-second producer lease. */
public final class InMemoryRequestReceiptStore implements RequestReceiptStore {
    public static final Duration LEASE = Duration.ofSeconds(30);
    private final Map<UUID, Entry> entries = new HashMap<>();

    @Override
    public synchronized ClaimResult claim(
            UUID requestToken,
            ConversationId conversationId,
            ResumeToken resumeToken,
            RequestFingerprint fingerprint,
            ContextHandle parentContextHandle,
            Instant now) {
        Objects.requireNonNull(requestToken, "requestToken");
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(resumeToken, "resumeToken");
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(now, "now");
        Entry existing = entries.get(requestToken);
        if (existing == null) {
            UUID leaseId = UUID.randomUUID();
            entries.put(requestToken, Entry.inProgress(
                    conversationId, resumeToken, fingerprint, parentContextHandle,
                    leaseId, now.plus(LEASE)));
            return ClaimResult.claimed(leaseId);
        }
        if (!existing.conversationId.equals(conversationId)
                || !existing.resumeToken.equals(resumeToken)
                || !existing.fingerprint.equals(fingerprint)) {
            return ClaimResult.conflict();
        }
        if (existing.completionReceipt != null) {
            return ClaimResult.completed(existing.completionReceipt);
        }
        if (now.isBefore(existing.leaseExpiresAt)) {
            return ClaimResult.inProgress(Duration.between(now, existing.leaseExpiresAt));
        }
        existing.leaseId = UUID.randomUUID();
        existing.leaseExpiresAt = now.plus(LEASE);
        return ClaimResult.claimed(existing.leaseId);
    }

    @Override
    public synchronized void complete(
            UUID requestToken, UUID leaseId, CompletionReceipt receipt, Instant now) {
        Entry entry = entries.get(requestToken);
        if (entry == null || entry.completionReceipt != null
                || leaseId == null || !leaseId.equals(entry.leaseId)
                || !entry.conversationId.equals(receipt.getConversationId())
                || !entry.fingerprint.equals(receipt.getFingerprint())) {
            throw new IllegalStateException("request receipt lease is unavailable");
        }
        entry.completionReceipt = receipt;
        entry.leaseExpiresAt = now;
    }

    @Override
    public synchronized Optional<CompletionReceipt> findCompleted(UUID requestToken, Instant now) {
        Entry entry = entries.get(requestToken);
        return entry == null || entry.completionReceipt == null
                ? Optional.empty() : Optional.of(entry.completionReceipt);
    }

    private static final class Entry {
        private final ConversationId conversationId;
        private final ResumeToken resumeToken;
        private final RequestFingerprint fingerprint;
        @SuppressWarnings("unused")
        private final ContextHandle parentContextHandle;
        private UUID leaseId;
        private Instant leaseExpiresAt;
        private CompletionReceipt completionReceipt;

        private Entry(
                ConversationId conversationId,
                ResumeToken resumeToken,
                RequestFingerprint fingerprint,
                ContextHandle parentContextHandle,
                UUID leaseId,
                Instant leaseExpiresAt) {
            this.conversationId = conversationId;
            this.resumeToken = resumeToken;
            this.fingerprint = fingerprint;
            this.parentContextHandle = parentContextHandle;
            this.leaseId = leaseId;
            this.leaseExpiresAt = leaseExpiresAt;
        }

        private static Entry inProgress(
                ConversationId conversationId,
                ResumeToken resumeToken,
                RequestFingerprint fingerprint,
                ContextHandle parentContextHandle,
                UUID leaseId,
                Instant leaseExpiresAt) {
            return new Entry(conversationId, resumeToken, fingerprint, parentContextHandle,
                    leaseId, leaseExpiresAt);
        }
    }
}
