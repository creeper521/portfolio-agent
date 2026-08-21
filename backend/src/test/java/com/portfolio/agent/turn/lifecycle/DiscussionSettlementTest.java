package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.turn.continuation.ActiveDiscussionPointer;
import com.portfolio.agent.turn.continuation.ClarificationStore;
import com.portfolio.agent.turn.continuation.ConversationSessionStore;
import com.portfolio.agent.turn.continuation.DiscussionStateMutation;
import com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.projection.PublicAgentTurn;
import com.portfolio.agent.turn.state.memory.InMemoryTurnExecutionStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DiscussionSettlementTest {
    private static final Instant NOW =
            Instant.parse("2026-08-20T08:00:00Z");

    @Test
    void publicTurnAndPointerReplacementSettleAtomically() {
        InMemoryConversationSessionStore sessions =
                new InMemoryConversationSessionStore();
        InMemoryTurnExecutionStore store = store(sessions);
        String conversationId = UUID.randomUUID().toString();
        byte[] tokenHash = new byte[32];
        ConversationSessionStore.Session session =
                new ConversationSessionStore.Session(
                        conversationId, tokenHash, NOW,
                        NOW.plus(Duration.ofMinutes(30)));
        TurnExecutionStore.SessionAccess access =
                TurnExecutionStore.SessionAccess.tentative(session);
        UUID requestId = UUID.randomUUID();
        byte[] fingerprint = new byte[32];
        TurnDeadline deadline = TurnDeadline.after(
                Duration.ofSeconds(5),
                Clock.fixed(NOW, ZoneOffset.UTC));
        store.claim(
                requestId, conversationId,
                RequestFingerprintSet.single(fingerprint),
                access, NOW, Duration.ofSeconds(35), deadline);
        ActiveDiscussionPointer pointer = new ActiveDiscussionPointer(
                "discussion_handle_123", "project-a",
                NOW.plus(Duration.ofMinutes(30)));

        boolean completed = store.complete(
                requestId, fingerprint,
                new PublicAgentTurn.Conversational(
                        requestId, "已进入项目讨论", List.of()),
                List.of(), List.of(), session, access,
                NOW.plusSeconds(1), deadline,
                DiscussionStateMutation.replace(null, pointer));

        assertThat(completed).isTrue();
        ConversationSessionStore.Session settled = sessions.find(
                List.of(tokenHash), NOW.plusSeconds(2), deadline)
                .orElseThrow();
        assertThat(settled.activeDiscussion()).contains(pointer);
        assertThat(settled.discussionRevision()).isEqualTo(1);
    }

    @Test
    void staleGenerationCannotCommitAReadOnlyDiscussionResult() {
        InMemoryConversationSessionStore sessions =
                new InMemoryConversationSessionStore();
        InMemoryTurnExecutionStore store = store(sessions);
        String conversationId = UUID.randomUUID().toString();
        byte[] tokenHash = new byte[32];
        ActiveDiscussionPointer current = new ActiveDiscussionPointer(
                "discussion_handle_new", "project-b",
                NOW.plus(Duration.ofMinutes(30)));
        ConversationSessionStore.Session session =
                new ConversationSessionStore.Session(
                        conversationId, tokenHash, NOW,
                        NOW.plus(Duration.ofMinutes(30)), current);
        sessions.save(session);
        TurnExecutionStore.SessionAccess access =
                TurnExecutionStore.SessionAccess.authenticated(
                        conversationId, tokenHash);
        UUID requestId = UUID.randomUUID();
        byte[] fingerprint = new byte[32];
        TurnDeadline deadline = TurnDeadline.after(
                Duration.ofSeconds(5),
                Clock.fixed(NOW, ZoneOffset.UTC));
        store.claim(
                requestId, conversationId,
                RequestFingerprintSet.single(fingerprint),
                access, NOW, Duration.ofSeconds(35), deadline);

        assertThat(store.complete(
                requestId, fingerprint,
                new PublicAgentTurn.Conversational(
                        requestId, "旧结果", List.of()),
                List.of(), List.of(), null, access,
                NOW.plusSeconds(1), deadline,
                DiscussionStateMutation.guard(
                        "discussion_handle_old"))).isFalse();
        assertThat(store.find(requestId).orElseThrow().getStatus())
                .isEqualTo(TurnExecutionRecord.Status.CLAIMED);
        assertThat(sessions.find(
                List.of(tokenHash), NOW.plusSeconds(2), deadline)
                .orElseThrow().discussionRevision()).isZero();
    }

    private InMemoryTurnExecutionStore store(
            InMemoryConversationSessionStore sessions) {
        return new InMemoryTurnExecutionStore(
                new ClarificationStore(
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        Duration.ofMinutes(5)),
                Duration.ofMinutes(30), sessions,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
