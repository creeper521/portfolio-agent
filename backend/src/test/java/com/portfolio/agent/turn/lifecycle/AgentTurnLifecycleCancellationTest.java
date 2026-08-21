package com.portfolio.agent.turn.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTurnLifecycleCancellationTest {
    @Test void cancelledTerminalGatePreventsCompletionFromWinning() {
        AgentStateStore store = mock(AgentStateStore.class);
        when(store.claim(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(TurnExecutionStore.ClaimResult.claimed());
        when(store.completeWithSession(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new TurnExecutionStore.SettlementResult(false, null));
        when(store.cancel(any(), any(), any())).thenReturn(true);
        AgentTurnLifecycleService service = LifecycleTestFixture.service(
                store, com.portfolio.agent.turn.planning.ResolvedGoalSet.conversational("你好"));
        AgentTurnLifecycleService.Result result = service.execute(
                null, new AgentTurnCommand.Ask(
                        UUID.randomUUID(), new AgentTurnCommand.FreeText("你好"), null, null));
        assertThat(result.status()).isEqualTo(AgentTurnLifecycleService.Status.CANCELLED);
        assertThat(result.turn()).isNull();
    }
}
