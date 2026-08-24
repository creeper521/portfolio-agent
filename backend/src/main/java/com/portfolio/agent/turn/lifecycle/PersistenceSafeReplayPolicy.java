package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.SemanticTurnPlan;
import com.portfolio.agent.turn.projection.PublicAgentTurn;

import java.util.List;
import java.util.Objects;

/** Selects persistence-safe replay for capability output that may contain free-form text. */
public final class PersistenceSafeReplayPolicy {
    public static final String BODY_NOT_RETAINED_CODE = "REPLAY_BODY_NOT_RETAINED";
    public static final String BODY_NOT_RETAINED_MESSAGE = "该回答未被保留，请重新提问。";

    public PublicAgentTurn forProviderBody(PublicAgentTurn liveTurn) {
        Objects.requireNonNull(liveTurn, "liveTurn");
        return bodyNotRetained(liveTurn.getRequestId());
    }

    public PublicAgentTurn forPlan(
            PublicAgentTurn liveTurn, SemanticTurnPlan plan) {
        Objects.requireNonNull(liveTurn, "liveTurn");
        Objects.requireNonNull(plan, "plan");
        boolean portfolioOnly = !plan.getTasks().isEmpty()
                && plan.getTasks().stream()
                .map(SemanticTask::getType)
                .allMatch(List.of(
                        SemanticTask.Type.PORTFOLIO_FACT,
                        SemanticTask.Type.PORTFOLIO_COMPARE,
                        SemanticTask.Type.PORTFOLIO_RECOMMEND)::contains);
        return portfolioOnly ? liveTurn : bodyNotRetained(liveTurn.getRequestId());
    }

    private PublicAgentTurn bodyNotRetained(java.util.UUID requestId) {
        return new PublicAgentTurn.CapabilityUnavailable(
                requestId, BODY_NOT_RETAINED_CODE,
                BODY_NOT_RETAINED_MESSAGE, false, List.of());
    }
}
