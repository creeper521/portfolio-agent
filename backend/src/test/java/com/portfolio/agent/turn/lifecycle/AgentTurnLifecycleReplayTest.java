package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.turn.projection.PublicAgentTurn;
import com.portfolio.agent.turn.state.memory.InMemoryTurnExecutionStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTurnLifecycleReplayTest {
    @Test void completedRequestReturnsTheExactStoredPublicSnapshot() {
        InMemoryTurnExecutionStore store = new InMemoryTurnExecutionStore();
        UUID requestId = UUID.randomUUID();
        AgentTurnCommand command = new AgentTurnCommand.Ask(
                requestId, new AgentTurnCommand.FreeText("你好"), null, null);
        byte[] fingerprint = new RequestFingerprintFactory(new byte[32]).fingerprint(command);
        String conversationId = LifecycleTestFixture.sessionResolver()
                .resolve(null, requestId,
                        com.portfolio.agent.turn.execution.TurnDeadline.after(
                                Duration.ofSeconds(5), java.time.Clock.fixed(
                                        LifecycleTestFixture.NOW, java.time.ZoneOffset.UTC)))
                .conversationId();
        com.portfolio.agent.turn.continuation.ConversationSessionStore.Session session =
                new com.portfolio.agent.turn.continuation.ConversationSessionStore.Session(
                        conversationId, new byte[32], LifecycleTestFixture.NOW,
                        LifecycleTestFixture.NOW.plus(Duration.ofMinutes(30)));
        TurnExecutionStore.SessionAccess access = TurnExecutionStore.SessionAccess.tentative(session);
        store.claim(requestId, conversationId, RequestFingerprintSet.single(fingerprint), access,
                LifecycleTestFixture.NOW, Duration.ofSeconds(10),
                com.portfolio.agent.turn.execution.TurnDeadline.after(
                        Duration.ofSeconds(5), java.time.Clock.fixed(
                                LifecycleTestFixture.NOW, java.time.ZoneOffset.UTC)));
        PublicAgentTurn snapshot = new PublicAgentTurn.Conversational(requestId, "你好", List.of());
        store.complete(requestId, fingerprint, snapshot, List.of(), List.of(), session, access,
                LifecycleTestFixture.NOW.plusSeconds(1),
                com.portfolio.agent.turn.execution.TurnDeadline.after(
                        Duration.ofSeconds(5), java.time.Clock.fixed(
                                LifecycleTestFixture.NOW, java.time.ZoneOffset.UTC)));
        AgentTurnLifecycleService service = LifecycleTestFixture.service(
                store, com.portfolio.agent.turn.planning.ResolvedGoalSet.conversational("不应执行"));

        AgentTurnLifecycleService.Result result = service.execute(
                null, command);
        assertThat(result.status()).isEqualTo(AgentTurnLifecycleService.Status.REPLAY);
        assertThat(result.turn()).isSameAs(snapshot);
    }

    @Test void lostFirstResponseReplaysAcrossKeyRotationWithFreshTokenAndStableConversation() {
        java.time.Clock clock = java.time.Clock.fixed(
                LifecycleTestFixture.NOW, java.time.ZoneOffset.UTC);
        com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore sessions =
                new com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore();
        InMemoryTurnExecutionStore store = new InMemoryTurnExecutionStore(
                new com.portfolio.agent.turn.continuation.ClarificationStore(
                        clock, Duration.ofMinutes(5)), Duration.ofMinutes(30), sessions, clock);
        byte[] previousKey = new byte[32];
        java.util.Arrays.fill(previousKey, (byte) 1);
        byte[] currentKey = new byte[32];
        java.util.Arrays.fill(currentKey, (byte) 2);
        UUID requestId = UUID.randomUUID();
        AgentTurnCommand command = new AgentTurnCommand.Ask(
                requestId, new AgentTurnCommand.FreeText("你好"), null, null);
        AgentTurnLifecycleService oldService = LifecycleTestFixture.service(
                store, com.portfolio.agent.turn.planning.ResolvedGoalSet.conversational("你好"),
                new RequestFingerprintFactory(previousKey),
                new com.portfolio.agent.turn.continuation.ConversationSessionResolver(
                        sessions, previousKey, clock, Duration.ofMinutes(30)), clock);
        AgentTurnLifecycleService.Result first = oldService.execute(null, command);

        AgentTurnLifecycleService rotatedService = LifecycleTestFixture.service(
                store, com.portfolio.agent.turn.planning.ResolvedGoalSet.conversational("不应执行"),
                new RequestFingerprintFactory(currentKey, List.of(previousKey)),
                new com.portfolio.agent.turn.continuation.ConversationSessionResolver(
                        sessions, currentKey, List.of(previousKey), clock,
                        Duration.ofMinutes(30)), clock);
        AgentTurnLifecycleService.Result replay = rotatedService.execute(null, command);

        assertThat(first.status()).isEqualTo(AgentTurnLifecycleService.Status.COMPLETED);
        assertThat(replay.status()).isEqualTo(AgentTurnLifecycleService.Status.REPLAY);
        assertThat(replay.conversation().conversationId())
                .isEqualTo(first.conversation().conversationId());
        assertThat(replay.conversation().resumeToken())
                .isNotEqualTo(first.conversation().resumeToken());
        assertThat(rotatedService.currentConversation(first.conversation().resumeToken()).authenticated())
                .isFalse();
        assertThat(rotatedService.currentConversation(replay.conversation().resumeToken()).authenticated())
                .isTrue();
    }

    @Test void clearedConversationCannotBeReusedUntilAbsoluteExpiryThenGetsFreshToken() {
        MutableClock clock = new MutableClock(LifecycleTestFixture.NOW);
        com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore sessions =
                new com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore();
        InMemoryTurnExecutionStore store = new InMemoryTurnExecutionStore(
                new com.portfolio.agent.turn.continuation.ClarificationStore(
                        clock, Duration.ofMinutes(5)), Duration.ofMinutes(30), sessions, clock);
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, (byte) 3);
        AgentTurnLifecycleService service = LifecycleTestFixture.service(
                store, com.portfolio.agent.turn.planning.ResolvedGoalSet.conversational("你好"),
                new RequestFingerprintFactory(key),
                new com.portfolio.agent.turn.continuation.ConversationSessionResolver(
                        sessions, key, clock, Duration.ofMinutes(30)), clock);
        UUID requestId = UUID.randomUUID();
        AgentTurnCommand command = new AgentTurnCommand.Ask(
                requestId, new AgentTurnCommand.FreeText("你好"), null, null);
        AgentTurnLifecycleService.Result first = service.execute(null, command);
        assertThat(service.clearConversation(first.conversation().resumeToken())).isTrue();

        AgentTurnLifecycleService.Result blocked = service.execute(null, command);
        assertThat(blocked.status()).isEqualTo(AgentTurnLifecycleService.Status.CANCELLED);
        assertThat(service.currentConversation(first.conversation().resumeToken()).authenticated())
                .isFalse();

        clock.advance(Duration.ofMinutes(30));
        AgentTurnLifecycleService.Result replacement = service.execute(null, command);
        assertThat(replacement.status()).isEqualTo(AgentTurnLifecycleService.Status.COMPLETED);
        assertThat(replacement.conversation().conversationId())
                .isEqualTo(first.conversation().conversationId());
        assertThat(replacement.conversation().resumeToken())
                .isNotEqualTo(first.conversation().resumeToken());
        assertThat(service.currentConversation(first.conversation().resumeToken()).authenticated())
                .isFalse();
        assertThat(service.currentConversation(replacement.conversation().resumeToken()).authenticated())
                .isTrue();
    }

    private static final class MutableClock extends java.time.Clock {
        private java.time.Instant current;
        private MutableClock(java.time.Instant current) { this.current = current; }
        private void advance(Duration duration) { current = current.plus(duration); }
        @Override public java.time.Instant instant() { return current; }
        @Override public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
        @Override public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
    }
}
