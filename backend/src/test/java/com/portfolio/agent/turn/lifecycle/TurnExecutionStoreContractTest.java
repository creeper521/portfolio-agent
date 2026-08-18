package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.turn.projection.PublicAgentTurn;
import com.portfolio.agent.turn.state.memory.InMemoryTurnExecutionStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TurnExecutionStoreContractTest {
    private final Instant now = Instant.parse("2026-08-18T00:00:00Z");

    @Test void sameRequestReplaysAndDifferentFingerprintConflicts() {
        TurnExecutionStore store = new InMemoryTurnExecutionStore();
        UUID requestId = UUID.randomUUID();
        byte[] fingerprint = {1, 2, 3};
        assertThat(store.claim(requestId, "conversation-1", fingerprint, now, Duration.ofSeconds(10)).status())
                .isEqualTo(TurnExecutionStore.ClaimResult.Status.CLAIMED);
        PublicAgentTurn snapshot = new PublicAgentTurn.Conversational(
                requestId, "你好", List.of());
        assertThat(store.complete(
                requestId, fingerprint, snapshot, List.of(), List.of(), null,
                now.plusSeconds(1))).isTrue();
        TurnExecutionStore.ClaimResult replay = store.claim(
                requestId, "conversation-1", fingerprint, now.plusSeconds(2), Duration.ofSeconds(10));
        assertThat(replay.status()).isEqualTo(TurnExecutionStore.ClaimResult.Status.REPLAY);
        assertThat(replay.replay()).isSameAs(snapshot);
        assertThat(store.claim(
                requestId, "conversation-1", new byte[]{9}, now, Duration.ofSeconds(10)).status())
                .isEqualTo(TurnExecutionStore.ClaimResult.Status.CONFLICT);
    }

    @Test void activeLeaseReturnsRetryAfterAndExpiredLeaseCanBeReclaimed() {
        TurnExecutionStore store = new InMemoryTurnExecutionStore();
        UUID requestId = UUID.randomUUID();
        byte[] fingerprint = {4};
        store.claim(requestId, "conversation-1", fingerprint, now, Duration.ofSeconds(10));
        TurnExecutionStore.ClaimResult active = store.claim(
                requestId, "conversation-1", fingerprint, now.plusSeconds(2), Duration.ofSeconds(10));
        assertThat(active.status()).isEqualTo(TurnExecutionStore.ClaimResult.Status.IN_PROGRESS);
        assertThat(active.retryAfterSeconds()).isEqualTo(8);
        assertThat(store.claim(
                requestId, "conversation-1", fingerprint, now.plusSeconds(11), Duration.ofSeconds(10)).status())
                .isEqualTo(TurnExecutionStore.ClaimResult.Status.CLAIMED);
    }

    @Test void cancelAndCompleteCompeteThroughOneTerminalGate() {
        TurnExecutionStore store = new InMemoryTurnExecutionStore();
        UUID requestId = UUID.randomUUID();
        byte[] fingerprint = {5};
        store.claim(requestId, "conversation-1", fingerprint, now, Duration.ofSeconds(10));
        assertThat(store.cancel(requestId, "conversation-1", now.plusSeconds(1))).isTrue();
        assertThat(store.complete(
                requestId, fingerprint,
                new PublicAgentTurn.Conversational(requestId, "不应提交", List.of()),
                List.of(), List.of(), null, now.plusSeconds(2))).isFalse();
        assertThat(store.find(requestId).orElseThrow().getStatus())
                .isEqualTo(TurnExecutionRecord.Status.CANCELLED);
    }
}
