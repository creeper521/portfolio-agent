package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.turn.projection.PublicAgentTurn;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTurnLifecycleContinuationTest {
    @Test void unknownOrCrossConversationHandleDoesNotLeakExistence() {
        AgentStateStore store = mock(AgentStateStore.class);
        when(store.claim(any(), any(), any(), any(), any()))
                .thenReturn(TurnExecutionStore.ClaimResult.claimed());
        when(store.findContext(any(), any(), any())).thenReturn(Optional.empty());
        when(store.complete(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);
        AgentTurnLifecycleService service = LifecycleTestFixture.service(
                store, com.portfolio.agent.turn.planning.ResolvedGoalSet.conversational("unused"));
        AgentTurnLifecycleService.Result result = service.execute(
                null, new AgentTurnCommand.Continue(
                        UUID.randomUUID(), "context_handle_123", null,
                        "继续说明", null, null));
        assertThat(result.status()).isEqualTo(AgentTurnLifecycleService.Status.COMPLETED);
        PublicAgentTurn.CapabilityUnavailable turn =
                (PublicAgentTurn.CapabilityUnavailable) result.turn();
        assertThat(turn.getCode()).isEqualTo("CONTINUATION_UNAVAILABLE");
        assertThat(turn.isRetryable()).isFalse();
    }
}
