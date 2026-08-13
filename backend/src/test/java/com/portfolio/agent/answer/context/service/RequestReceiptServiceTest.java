package com.portfolio.agent.answer.context.service;

import com.portfolio.agent.answer.context.adapter.memory.InMemoryRequestReceiptStore;
import com.portfolio.agent.answer.context.domain.CompletionReceipt;
import com.portfolio.agent.answer.context.domain.ConversationContinuationStatus;
import com.portfolio.agent.answer.context.domain.ConversationId;
import com.portfolio.agent.answer.context.domain.RequestFingerprint;
import com.portfolio.agent.answer.context.domain.ResumeToken;
import com.portfolio.agent.answer.context.gateway.RequestReceiptStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestReceiptServiceTest {
    private static final Instant START = Instant.parse("2026-08-12T04:00:00Z");

    @Test
    void uuidV4IsRequiredAndFingerprintIsOpaqueHash() {
        RequestReceiptService service = new RequestReceiptService(new InMemoryRequestReceiptStore());
        ConversationId conversationId = ConversationId.random();
        ResumeToken token = ResumeToken.issue();
        RequestFingerprint fingerprint = RequestFingerprint.sha256Canonical(
                "question=portfolio|context=none|contentVersion=v1");
        assertTrue(fingerprint.value().length() > 40);
        UUID uuidV1 = UUID.fromString("00000000-0000-1000-8000-000000000001");

        assertThrows(IllegalArgumentException.class,
                () -> service.claim(uuidV1, conversationId, token, fingerprint, null, START));
    }

    @Test
    void sameRequestIsLeasedThenCompletedAndCompletedRetryDoesNotReexecute() {
        InMemoryRequestReceiptStore store = new InMemoryRequestReceiptStore();
        RequestReceiptService service = new RequestReceiptService(store);
        ConversationId conversationId = ConversationId.random();
        ResumeToken token = ResumeToken.issue();
        RequestFingerprint fingerprint = RequestFingerprint.sha256Canonical("normalized-request-v1");
        UUID requestToken = UUID.randomUUID();

        RequestReceiptStore.ClaimResult first = service.claim(
                requestToken, conversationId, token, fingerprint, null, START);
        RequestReceiptStore.ClaimResult duplicate = service.claim(
                requestToken, conversationId, token, fingerprint, null, START.plusSeconds(1));
        CompletionReceipt completed = service.complete(
                requestToken, first.getLeaseId().orElseThrow(), conversationId, fingerprint, null,
                ConversationContinuationStatus.AVAILABLE, START.plusSeconds(2));
        RequestReceiptStore.ClaimResult retry = service.claim(
                requestToken, conversationId, token, fingerprint, null, START.plusSeconds(3));

        assertEquals(RequestReceiptStore.ClaimResult.Status.CLAIMED, first.getStatus());
        assertEquals(RequestReceiptStore.ClaimResult.Status.IN_PROGRESS, duplicate.getStatus());
        assertEquals(ConversationContinuationStatus.AVAILABLE, completed.getContinuationStatus());
        assertEquals(RequestReceiptStore.ClaimResult.Status.ALREADY_COMPLETED, retry.getStatus());
        assertEquals(completed, retry.getCompletionReceipt().orElseThrow());
    }

    @Test
    void differentFingerprintOrResumeTokenConflictsAndExpiredLeaseCanBeTakenOver() {
        InMemoryRequestReceiptStore store = new InMemoryRequestReceiptStore();
        RequestReceiptService service = new RequestReceiptService(store);
        ConversationId conversationId = ConversationId.random();
        ResumeToken token = ResumeToken.issue();
        UUID requestToken = UUID.randomUUID();
        RequestFingerprint firstFingerprint = RequestFingerprint.sha256Canonical("first");
        RequestFingerprint otherFingerprint = RequestFingerprint.sha256Canonical("other");
        RequestReceiptStore.ClaimResult first = service.claim(
                requestToken, conversationId, token, firstFingerprint, null, START);

        assertEquals(RequestReceiptStore.ClaimResult.Status.IDEMPOTENCY_KEY_CONFLICT,
                service.claim(requestToken, conversationId, token, otherFingerprint, null, START).getStatus());
        assertEquals(RequestReceiptStore.ClaimResult.Status.IDEMPOTENCY_KEY_CONFLICT,
                service.claim(requestToken, conversationId, ResumeToken.issue(), firstFingerprint, null, START).getStatus());
        RequestReceiptStore.ClaimResult takeover = service.claim(
                requestToken, conversationId, token, firstFingerprint, null, START.plusSeconds(31));

        assertEquals(RequestReceiptStore.ClaimResult.Status.CLAIMED, takeover.getStatus());
        assertTrue(!first.getLeaseId().orElseThrow().equals(takeover.getLeaseId().orElseThrow()));
        assertThrows(IllegalStateException.class, () -> service.complete(
                requestToken, first.getLeaseId().orElseThrow(), conversationId, firstFingerprint, null,
                ConversationContinuationStatus.AVAILABLE, START.plusSeconds(32)));
    }
}
