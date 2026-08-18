package com.portfolio.agent.turn.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTurnLifecycleSettlementFailureTest {
    @Test void readOnlyAnswerSurvivesPostClaimSettlementFailureWithoutContinuation() {
        TurnExecutionStore store = mock(TurnExecutionStore.class);
        when(store.claim(any(), any(), any(), any(), any()))
                .thenReturn(TurnExecutionStore.ClaimResult.claimed());
        when(store.complete(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("state unavailable"));
        AgentTurnLifecycleService service = LifecycleTestFixture.service(
                store, com.portfolio.agent.turn.planning.ResolvedGoalSet.conversational("你好"));
        AgentTurnCommand command = new AgentTurnCommand.Ask(
                UUID.randomUUID(), new AgentTurnCommand.FreeText("你好"), null, null);

        AgentTurnLifecycleService.Result result = service.execute(
                null, command);
        assertThat(result.status()).isEqualTo(AgentTurnLifecycleService.Status.COMPLETED);
        assertThat(result.settlementFailed()).isTrue();
        assertThat(result.turn()).isInstanceOf(
                com.portfolio.agent.turn.projection.PublicAgentTurn.Conversational.class);
    }
}
