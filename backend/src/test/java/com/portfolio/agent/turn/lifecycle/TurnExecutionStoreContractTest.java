package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.turn.projection.PublicAgentTurn;
import com.portfolio.agent.turn.state.memory.InMemoryTurnExecutionStore;
import com.portfolio.agent.turn.execution.TurnDeadline;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TurnExecutionStoreContractTest {
    private final Instant now = Instant.parse("2026-08-18T00:00:00Z");
    private final java.util.Map<UUID, com.portfolio.agent.turn.continuation.ConversationSessionStore.Session>
            sessionsByRequest = new java.util.HashMap<>();

    @Test void sameRequestReplaysAndDifferentFingerprintConflicts() {
        TurnExecutionStore store = store();
        UUID requestId = UUID.randomUUID();
        byte[] fingerprint = {1, 2, 3};
        assertThat(claim(store, requestId, "conversation-1", fingerprint, now,
                Duration.ofSeconds(10), deadline()).status())
                .isEqualTo(TurnExecutionStore.ClaimResult.Status.CLAIMED);
        PublicAgentTurn snapshot = new PublicAgentTurn.Conversational(
                requestId, "你好", List.of());
        assertThat(complete(store,
                requestId, fingerprint, snapshot, List.of(), List.of(), null,
                now.plusSeconds(1), deadline())).isTrue();
        TurnExecutionStore.ClaimResult replay = claim(store,
                requestId, "conversation-1", fingerprint, now.plusSeconds(2),
                Duration.ofSeconds(10), deadline());
        assertThat(replay.status()).isEqualTo(TurnExecutionStore.ClaimResult.Status.REPLAY);
        assertThat(replay.replay()).isSameAs(snapshot);
        assertThat(claim(store,
                requestId, "conversation-1", new byte[]{9}, now,
                Duration.ofSeconds(10), deadline()).status())
                .isEqualTo(TurnExecutionStore.ClaimResult.Status.CONFLICT);
    }

    @Test void activeLeaseReturnsRetryAfterAndExpiredLeaseCanBeReclaimed() {
        TurnExecutionStore store = store();
        UUID requestId = UUID.randomUUID();
        byte[] fingerprint = {4};
        claim(store, requestId, "conversation-1", fingerprint, now,
                Duration.ofSeconds(10), deadline());
        TurnExecutionStore.ClaimResult active = claim(store,
                requestId, "conversation-1", fingerprint, now.plusSeconds(2),
                Duration.ofSeconds(10), deadline());
        assertThat(active.status()).isEqualTo(TurnExecutionStore.ClaimResult.Status.IN_PROGRESS);
        assertThat(active.retryAfterSeconds()).isEqualTo(8);
        assertThat(claim(store,
                requestId, "conversation-1", fingerprint, now.plusSeconds(11),
                Duration.ofSeconds(10), deadline()).status())
                .isEqualTo(TurnExecutionStore.ClaimResult.Status.CLAIMED);
    }

    @Test void cancelAndCompleteCompeteThroughOneTerminalGate() {
        TurnExecutionStore store = store();
        UUID requestId = UUID.randomUUID();
        byte[] fingerprint = {5};
        claim(store, requestId, "conversation-1", fingerprint, now,
                Duration.ofSeconds(10), deadline());
        assertThat(store.cancel(requestId, "conversation-1", now.plusSeconds(1))).isTrue();
        assertThat(complete(store,
                requestId, fingerprint,
                new PublicAgentTurn.Conversational(requestId, "不应提交", List.of()),
                List.of(), List.of(), null, now.plusSeconds(2), deadline())).isFalse();
        assertThat(store.find(requestId).orElseThrow().getStatus())
                .isEqualTo(TurnExecutionRecord.Status.CANCELLED);
    }

    @Test void replayExpiresAtThirtyMinutesWithoutReadExtension() {
        com.portfolio.agent.turn.continuation.ClarificationStore clarifications =
                new com.portfolio.agent.turn.continuation.ClarificationStore(
                        Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(5));
        TurnExecutionStore store = new InMemoryTurnExecutionStore(
                clarifications, Duration.ofMinutes(30));
        UUID requestId = UUID.randomUUID();
        byte[] fingerprint = {7};
        claim(store, requestId, "conversation-1", fingerprint, now,
                Duration.ofSeconds(35), deadline());
        complete(store, requestId, fingerprint,
                new PublicAgentTurn.Conversational(requestId, "完成", List.of()),
                List.of(), List.of(), null, now, deadline());

        assertThat(claim(store,
                requestId, "conversation-1", fingerprint,
                now.plus(Duration.ofMinutes(29)), Duration.ofSeconds(35), deadline()).status())
                .isEqualTo(TurnExecutionStore.ClaimResult.Status.REPLAY);
        assertThat(claim(store,
                requestId, "conversation-1", fingerprint,
                now.plus(Duration.ofMinutes(30)), Duration.ofSeconds(35), deadline()).status())
                .isEqualTo(TurnExecutionStore.ClaimResult.Status.CLAIMED);
    }

    @Test void inMemoryCleanupUsesTheSameAbsoluteExpiryAndBatchLimit() {
        com.portfolio.agent.turn.continuation.ClarificationStore clarifications =
                new com.portfolio.agent.turn.continuation.ClarificationStore(
                        Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(5));
        InMemoryTurnExecutionStore store = new InMemoryTurnExecutionStore(
                clarifications, Duration.ofMinutes(30));
        claim(store, UUID.randomUUID(), "conversation-1", new byte[]{1}, now,
                Duration.ofSeconds(35), deadline());
        claim(store, UUID.randomUUID(), "conversation-1", new byte[]{2}, now,
                Duration.ofSeconds(35), deadline());

        assertThat(store.cleanup(now.plus(Duration.ofMinutes(30)), 1).total()).isEqualTo(1);
        assertThat(store.cleanup(now.plus(Duration.ofMinutes(30)), 1).total()).isEqualTo(1);
        assertThat(store.cleanup(now.plus(Duration.ofMinutes(30)), 1).total()).isZero();
    }

    @Test void inMemoryFindAndContextAreHiddenAtTheExecutionAbsoluteExpiry() {
        MutableClock clock = new MutableClock(now);
        InMemoryTurnExecutionStore store = new InMemoryTurnExecutionStore(
                new com.portfolio.agent.turn.continuation.ClarificationStore(
                        clock, Duration.ofMinutes(5)), Duration.ofMinutes(30),
                new com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore(), clock);
        UUID requestId = UUID.randomUUID();
        byte[] fingerprint = new byte[32];
        com.portfolio.agent.turn.continuation.ContinuationContext.PortfolioFact context =
                new com.portfolio.agent.turn.continuation.ContinuationContext.PortfolioFact(
                        "context_memory_clamp_1", "conversation-1", "public-1",
                        now.plus(Duration.ofHours(1)), java.util.Set.of("project-a"),
                        java.util.Set.of("OVERVIEW"));
        claim(store, requestId, "conversation-1", fingerprint, now,
                Duration.ofSeconds(35), deadline());
        complete(store, requestId, fingerprint,
                new PublicAgentTurn.Conversational(requestId, "完成", List.of()),
                List.of(context), List.of(), null, now, deadline());

        assertThat(store.findContext(
                "conversation-1", "context_memory_clamp_1",
                now.plus(Duration.ofMinutes(29)), deadline())).isPresent();
        clock.advance(Duration.ofMinutes(30));
        assertThat(store.find(requestId)).isEmpty();
        assertThat(store.findContext(
                "conversation-1", "context_memory_clamp_1", clock.instant(),
                new com.portfolio.agent.turn.execution.TurnDeadline(
                        clock.instant().plusSeconds(1), clock))).isEmpty();
    }

    @Test void inMemoryAtomicClearPreventsConcurrentClaimAndSettlementResurrection() throws Exception {
        com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore sessions =
                new com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore();
        InMemoryTurnExecutionStore store = new InMemoryTurnExecutionStore(
                new com.portfolio.agent.turn.continuation.ClarificationStore(
                        Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(5)),
                Duration.ofMinutes(30), sessions, Clock.fixed(now, ZoneOffset.UTC));
        String conversationId = java.util.UUID.randomUUID().toString();
        byte[] tokenHash = new byte[32];
        sessions.save(new com.portfolio.agent.turn.continuation.ConversationSessionStore.Session(
                conversationId, tokenHash, now, now.plus(Duration.ofMinutes(30))));
        TurnExecutionStore.SessionAccess access =
                TurnExecutionStore.SessionAccess.authenticated(conversationId, tokenHash);
        UUID claimedId = UUID.randomUUID();
        byte[] fingerprint = new byte[32];
        store.claim(claimedId, conversationId, RequestFingerprintSet.single(fingerprint),
                access, now, Duration.ofSeconds(35), deadline());

        runRace(
                () -> store.complete(claimedId, fingerprint,
                        new PublicAgentTurn.Conversational(claimedId, "竞态", List.of()),
                        List.of(), List.of(), null, access, now.plusSeconds(1), deadline()),
                () -> store.clearConversation(conversationId, tokenHash, now.plusSeconds(1)));

        assertThat(store.find(claimedId)).isEmpty();
        assertThat(sessions.find(List.of(tokenHash), now.plusSeconds(2), deadline())).isEmpty();
        UUID lateClaim = UUID.randomUUID();
        assertThat(store.claim(
                lateClaim, conversationId, RequestFingerprintSet.single(fingerprint),
                access, now.plusSeconds(2), Duration.ofSeconds(35), deadline()).status())
                .isEqualTo(TurnExecutionStore.ClaimResult.Status.CANCELLED);
        assertThat(store.find(lateClaim)).isEmpty();
        byte[] replacementHash = new byte[32];
        replacementHash[0] = 1;
        com.portfolio.agent.turn.continuation.ConversationSessionStore.Session replacement =
                new com.portfolio.agent.turn.continuation.ConversationSessionStore.Session(
                        conversationId, replacementHash, now.plusSeconds(3),
                        now.plus(Duration.ofMinutes(30)).plusSeconds(3));
        TurnExecutionStore.SessionAccess replacementAccess =
                TurnExecutionStore.SessionAccess.tentative(replacement);
        assertThat(store.claim(
                lateClaim, conversationId, RequestFingerprintSet.single(fingerprint),
                replacementAccess, now.plusSeconds(3), Duration.ofSeconds(35), deadline()).status())
                .isEqualTo(TurnExecutionStore.ClaimResult.Status.CANCELLED);
        replacement = new com.portfolio.agent.turn.continuation.ConversationSessionStore.Session(
                conversationId, replacementHash, now.plus(Duration.ofMinutes(30)),
                now.plus(Duration.ofMinutes(60)));
        replacementAccess = TurnExecutionStore.SessionAccess.tentative(replacement);
        assertThat(store.claim(
                lateClaim, conversationId, RequestFingerprintSet.single(fingerprint),
                replacementAccess, now.plus(Duration.ofMinutes(30)),
                Duration.ofSeconds(35), deadline()).status())
                .isEqualTo(TurnExecutionStore.ClaimResult.Status.CLAIMED);
        assertThat(store.complete(
                lateClaim, fingerprint,
                new PublicAgentTurn.Conversational(lateClaim, "新会话", List.of()),
                List.of(), List.of(), replacement, replacementAccess,
                now.plus(Duration.ofMinutes(30)).plusSeconds(1), deadline())).isTrue();
        assertThat(sessions.find(
                List.of(replacementHash), now.plus(Duration.ofMinutes(30)).plusSeconds(2),
                deadline())).isPresent();
    }

    @Test void inMemoryReplayPersistsCurrentFingerprintKeyId() {
        InMemoryTurnExecutionStore store = store();
        UUID requestId = UUID.randomUUID();
        String conversationId = "conversation-1";
        byte[] v1 = new byte[32];
        byte[] v2 = new byte[32];
        v2[0] = 1;
        com.portfolio.agent.turn.continuation.ConversationSessionStore.Session session =
                new com.portfolio.agent.turn.continuation.ConversationSessionStore.Session(
                        conversationId, new byte[32], now, now.plus(Duration.ofMinutes(30)));
        TurnExecutionStore.SessionAccess access = TurnExecutionStore.SessionAccess.tentative(session);
        store.claim(requestId, conversationId,
                new RequestFingerprintSet("v1", v1, List.of()), access, now,
                Duration.ofSeconds(35), deadline());
        store.complete(requestId, v1,
                new PublicAgentTurn.Conversational(requestId, "完成", List.of()),
                List.of(), List.of(), session, access, now.plusSeconds(1), deadline());

        assertThat(store.claim(
                requestId, conversationId,
                new RequestFingerprintSet("v2", v2, List.of(
                        new RequestFingerprintSet.Candidate("v1", v1))),
                TurnExecutionStore.SessionAccess.authenticated(
                        conversationId, session.tokenHash()),
                now.plusSeconds(2), Duration.ofSeconds(35), deadline()).status())
                .isEqualTo(TurnExecutionStore.ClaimResult.Status.REPLAY);
        assertThat(store.find(requestId).orElseThrow().getFingerprintKeyId()).isEqualTo("v2");
        assertThat(store.claim(
                requestId, conversationId,
                new RequestFingerprintSet("v2", v2, List.of()),
                TurnExecutionStore.SessionAccess.authenticated(
                        conversationId, session.tokenHash()),
                now.plusSeconds(3), Duration.ofSeconds(35), deadline()).status())
                .isEqualTo(TurnExecutionStore.ClaimResult.Status.REPLAY);
    }

    private TurnDeadline deadline() {
        return TurnDeadline.after(
                Duration.ofSeconds(5), Clock.fixed(now, ZoneOffset.UTC));
    }

    private TurnExecutionStore.ClaimResult claim(
            TurnExecutionStore store, UUID requestId, String conversationId,
            byte[] fingerprint, Instant at, Duration lease, TurnDeadline deadline) {
        byte[] tokenHash = java.util.Arrays.copyOf(
                (at + requestId.toString()).getBytes(
                        java.nio.charset.StandardCharsets.UTF_8), 32);
        com.portfolio.agent.turn.continuation.ConversationSessionStore.Session session =
                new com.portfolio.agent.turn.continuation.ConversationSessionStore.Session(
                        conversationId, tokenHash, at, at.plus(Duration.ofMinutes(30)));
        sessionsByRequest.put(requestId, session);
        return store.claim(requestId, conversationId,
                RequestFingerprintSet.single(fingerprint),
                TurnExecutionStore.SessionAccess.tentative(session), at, lease, deadline);
    }

    private boolean complete(
            TurnExecutionStore store, UUID requestId, byte[] fingerprint,
            PublicAgentTurn snapshot,
            List<com.portfolio.agent.turn.continuation.ContinuationContext> contexts,
            List<com.portfolio.agent.turn.continuation.ClarificationStore.Record> challenges,
            com.portfolio.agent.turn.continuation.ConversationSessionStore.Session ignored,
            Instant at, TurnDeadline deadline) {
        com.portfolio.agent.turn.continuation.ConversationSessionStore.Session session =
                sessionsByRequest.get(requestId);
        return store.complete(requestId, fingerprint, snapshot, contexts, challenges,
                session, TurnExecutionStore.SessionAccess.tentative(session), at, deadline);
    }

    private InMemoryTurnExecutionStore store() {
        return new InMemoryTurnExecutionStore(
                new com.portfolio.agent.turn.continuation.ClarificationStore(
                        Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(5)),
                Duration.ofMinutes(30),
                new com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore(),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private void runRace(
            java.util.concurrent.Callable<?> first,
            java.util.concurrent.Callable<?> second) throws Exception {
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        try (java.util.concurrent.ExecutorService executor =
                     java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            java.util.concurrent.Future<?> firstResult = executor.submit(
                    () -> { start.await(); return first.call(); });
            java.util.concurrent.Future<?> secondResult = executor.submit(
                    () -> { start.await(); return second.call(); });
            start.countDown();
            firstResult.get();
            secondResult.get();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant current;
        private MutableClock(Instant current) { this.current = current; }
        private void advance(Duration duration) { current = current.plus(duration); }
        @Override public Instant instant() { return current; }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }
}
