package com.portfolio.agent.answer.context.gateway;

import com.portfolio.agent.answer.context.domain.CompletionReceipt;
import com.portfolio.agent.answer.context.domain.ContextHandle;
import com.portfolio.agent.answer.context.domain.ConversationId;
import com.portfolio.agent.answer.context.domain.RequestFingerprint;
import com.portfolio.agent.answer.context.domain.ResumeToken;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for request idempotency and public completion state. */
public interface RequestReceiptStore {
    ClaimResult claim(
            UUID requestToken,
            ConversationId conversationId,
            ResumeToken resumeToken,
            RequestFingerprint fingerprint,
            ContextHandle parentContextHandle,
            Instant now);

    void complete(UUID requestToken, UUID leaseId, CompletionReceipt receipt, Instant now);

    Optional<CompletionReceipt> findCompleted(UUID requestToken, Instant now);

    final class ClaimResult {
        public enum Status {
            CLAIMED,
            IN_PROGRESS,
            ALREADY_COMPLETED,
            IDEMPOTENCY_KEY_CONFLICT
        }

        private final Status status;
        private final CompletionReceipt completionReceipt;
        private final Duration retryAfter;
        private final UUID leaseId;

        private ClaimResult(
                Status status, CompletionReceipt completionReceipt, Duration retryAfter, UUID leaseId) {
            this.status = status;
            this.completionReceipt = completionReceipt;
            this.retryAfter = retryAfter;
            this.leaseId = leaseId;
        }

        public static ClaimResult claimed(UUID leaseId) {
            return new ClaimResult(Status.CLAIMED, null, null, leaseId);
        }
        public static ClaimResult inProgress(Duration retryAfter) {
            return new ClaimResult(Status.IN_PROGRESS, null, retryAfter, null);
        }
        public static ClaimResult completed(CompletionReceipt receipt) {
            return new ClaimResult(Status.ALREADY_COMPLETED, receipt, null, null);
        }
        public static ClaimResult conflict() {
            return new ClaimResult(Status.IDEMPOTENCY_KEY_CONFLICT, null, null, null);
        }
        public Status getStatus() { return status; }
        public Optional<CompletionReceipt> getCompletionReceipt() {
            return Optional.ofNullable(completionReceipt);
        }
        public Optional<Duration> getRetryAfter() { return Optional.ofNullable(retryAfter); }
        public Optional<UUID> getLeaseId() { return Optional.ofNullable(leaseId); }
    }
}
