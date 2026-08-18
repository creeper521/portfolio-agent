package com.portfolio.agent.turn.planning;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoalBoundaryPolicyTest {

    private final GoalBoundaryPolicy policy = new GoalBoundaryPolicy();

    @Test
    void stableGeneralKnowledgeMayProceed() {
        ResolvedGoalSet result = policy.apply(GoalResolverTest.generalProposal(
                GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION));
        assertThat(result.getKind()).isEqualTo(ResolvedGoalSet.Kind.GOALS);
    }

    @Test
    void currentAndHighRiskKnowledgeBecomeBoundary() {
        assertThat(policy.apply(GoalResolverTest.generalProposal(
                GoalKnowledgeRequirement.CURRENT_EXTERNAL_INFORMATION)).getKind())
                .isEqualTo(ResolvedGoalSet.Kind.BOUNDARY);
        assertThat(policy.apply(GoalResolverTest.generalProposal(
                GoalKnowledgeRequirement.HIGH_RISK_ADVICE)).getKind())
                .isEqualTo(ResolvedGoalSet.Kind.BOUNDARY);
    }
}
