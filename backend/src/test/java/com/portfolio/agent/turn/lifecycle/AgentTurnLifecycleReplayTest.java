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
                .resolve(null, requestId).conversationId();
        store.claim(requestId, conversationId, fingerprint,
                LifecycleTestFixture.NOW, Duration.ofSeconds(10));
        PublicAgentTurn snapshot = new PublicAgentTurn.Conversational(requestId, "你好", List.of());
        store.complete(requestId, fingerprint, snapshot, List.of(), List.of(), null,
                LifecycleTestFixture.NOW.plusSeconds(1));
        AgentTurnLifecycleService service = LifecycleTestFixture.service(
                store, com.portfolio.agent.turn.planning.ResolvedGoalSet.conversational("不应执行"));

        AgentTurnLifecycleService.Result result = service.execute(
                null, command);
        assertThat(result.status()).isEqualTo(AgentTurnLifecycleService.Status.REPLAY);
        assertThat(result.turn()).isSameAs(snapshot);
    }
}
