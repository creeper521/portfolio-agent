package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.turn.execution.SemanticTurnEngine;
import com.portfolio.agent.turn.planning.ResolvedGoalSet;
import com.portfolio.agent.turn.planning.SemanticPlanCompiler;
import com.portfolio.agent.turn.projection.PublicAgentTurn;
import com.portfolio.agent.turn.state.memory.InMemoryTurnExecutionStore;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AgentTurnLifecycleBoundaryTest {

    @Test
    void boundarySettlesWithoutCompilingOrExecutingAGeneralPlan() {
        SemanticPlanCompiler compiler = mock(SemanticPlanCompiler.class);
        SemanticTurnEngine engine = mock(SemanticTurnEngine.class);
        AgentTurnLifecycleService service = LifecycleTestFixture.service(
                new InMemoryTurnExecutionStore(),
                ResolvedGoalSet.boundary(
                        "\u8be5\u6bd4\u8f83\u8bf7\u6c42\u7684\u4e3b\u4f53\u4e0e\u7ef4\u5ea6\u7ec4\u5408\u8d85\u51fa\u5f53\u524d\u80fd\u529b\u4e0a\u9650\u3002"),
                compiler, engine);
        UUID requestId = UUID.randomUUID();

        AgentTurnLifecycleService.Result result = service.execute(
                null, new AgentTurnCommand.Ask(
                        requestId, AgentTurnCommand.ModelSelection.none(),
                        new AgentTurnCommand.FreeText("\u6bd4\u8f83\u591a\u4e2a\u4e3b\u4f53\u7684\u591a\u4e2a\u7ef4\u5ea6"),
                        null, null));

        assertThat(result.status()).isEqualTo(
                AgentTurnLifecycleService.Status.COMPLETED);
        assertThat(result.turn()).isInstanceOf(PublicAgentTurn.Boundary.class);
        PublicAgentTurn.Boundary boundary =
                (PublicAgentTurn.Boundary) result.turn();
        assertThat(boundary.getRequestId()).isEqualTo(requestId);
        assertThat(boundary.getCode()).isEqualTo("OUT_OF_SCOPE");
        assertThat(boundary.getMessage()).isEqualTo(
                "\u8be5\u6bd4\u8f83\u8bf7\u6c42\u7684\u4e3b\u4f53\u4e0e\u7ef4\u5ea6\u7ec4\u5408\u8d85\u51fa\u5f53\u524d\u80fd\u529b\u4e0a\u9650\u3002");
        verifyNoInteractions(compiler, engine);
    }
}
