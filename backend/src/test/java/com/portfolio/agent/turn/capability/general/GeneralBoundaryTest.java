package com.portfolio.agent.turn.capability.general;

import com.portfolio.agent.turn.planning.GoalBoundaryPolicy;
import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.GoalKnowledgeRequirement;
import com.portfolio.agent.turn.planning.GoalRequestedOutput;
import com.portfolio.agent.turn.planning.ResolvedGoalSet;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GeneralBoundaryTest {
    @Test void currentAndHighRiskGeneralGoalsNeverReachExecution() {
        GoalBoundaryPolicy policy = new GoalBoundaryPolicy();
        assertThat(policy.apply(proposal(GoalKnowledgeRequirement.CURRENT_EXTERNAL_INFORMATION)).getKind())
                .isEqualTo(ResolvedGoalSet.Kind.BOUNDARY);
        assertThat(policy.apply(proposal(GoalKnowledgeRequirement.HIGH_RISK_ADVICE)).getKind())
                .isEqualTo(ResolvedGoalSet.Kind.BOUNDARY);
    }

    private UserGoalProposal proposal(GoalKnowledgeRequirement requirement) {
        UserGoalProposal.InputAnchor anchor = new UserGoalProposal.InputAnchor("并发控制", 0);
        return new UserGoalProposal(List.of(new UserGoalProposal.ProposedGoal(
                "general-goal", GoalKind.GENERAL_EXPLANATION, anchor, List.of(),
                Set.of(GoalRequestedOutput.EXPLANATION), requirement,
                new UserGoalProposal.GeneralExplanationParameters(anchor, UserGoalProposal.Depth.STANDARD))));
    }
}
