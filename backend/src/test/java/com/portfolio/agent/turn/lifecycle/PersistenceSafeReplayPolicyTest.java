package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.turn.continuation.ContinuationReference;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.SemanticTurnPlan;
import com.portfolio.agent.turn.projection.PublicAgentTurn;
import com.portfolio.agent.turn.projection.SuggestedAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PersistenceSafeReplayPolicyTest {
    private final PersistenceSafeReplayPolicy policy =
            new PersistenceSafeReplayPolicy();

    @Test void providerDerivedBodyIsReplacedByFixedTerminal() {
        UUID requestId = UUID.randomUUID();
        PublicAgentTurn live = new PublicAgentTurn.Conversational(
                requestId, "provider-body-sentinel", List.of());

        PublicAgentTurn persisted = policy.forProviderBody(live);

        PublicAgentTurn.CapabilityUnavailable unavailable =
                (PublicAgentTurn.CapabilityUnavailable) persisted;
        assertThat(unavailable.getCode()).isEqualTo("REPLAY_BODY_NOT_RETAINED");
        assertThat(unavailable.getMessage()).isEqualTo("该回答未被保留，请重新提问。");
        assertThat(unavailable.getMessage()).doesNotContain("sentinel");
    }

    @Test void onlyPortfolioPlansAreExactAndContinuationHandleRemainsUsable() {
        String handle = "context_handle_456";
        PublicAgentTurn live = new PublicAgentTurn.Boundary(
                UUID.randomUUID(), "FIXED", "固定公开文本",
                List.of(new SuggestedAction(
                        "exit", "结束", null,
                        ContinuationReference.exitContext(handle))));

        for (SemanticTask.Type type : List.of(
                SemanticTask.Type.PORTFOLIO_FACT,
                SemanticTask.Type.PORTFOLIO_COMPARE,
                SemanticTask.Type.PORTFOLIO_RECOMMEND)) {
            assertThat(policy.forPlan(live, plan(type))).isSameAs(live);
        }
        assertThat(((PublicAgentTurn.Boundary) policy.forPlan(
                live, plan(SemanticTask.Type.PORTFOLIO_FACT)))
                .getSuggestedActions().getFirst().getContinuation().getContextHandle())
                .isEqualTo(handle);
        for (SemanticTask.Type type : List.of(
                SemanticTask.Type.GENERAL_EXPLANATION,
                SemanticTask.Type.GENERAL_COMPARISON,
                SemanticTask.Type.CROSS_DOMAIN_SYNTHESIS)) {
            assertThat(policy.forPlan(live, plan(type)))
                    .isInstanceOf(PublicAgentTurn.CapabilityUnavailable.class);
        }
    }

    private SemanticTurnPlan plan(SemanticTask.Type type) {
        SemanticTask task = mock(SemanticTask.class);
        when(task.getType()).thenReturn(type);
        SemanticTurnPlan plan = mock(SemanticTurnPlan.class);
        when(plan.getTasks()).thenReturn(List.of(task));
        return plan;
    }
}
