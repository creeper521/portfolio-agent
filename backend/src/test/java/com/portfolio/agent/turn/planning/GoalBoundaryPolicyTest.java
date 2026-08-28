package com.portfolio.agent.turn.planning;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void highRiskBoundaryTakesPrecedenceOverComparisonCertificationBoundary() {
        ResolvedGoalSet result = policy.apply(comparisonProposal(
                2, 2, GoalKnowledgeRequirement.HIGH_RISK_ADVICE));

        assertThat(result.getKind()).isEqualTo(ResolvedGoalSet.Kind.BOUNDARY);
        assertThat(result.getMessage().orElseThrow())
                .contains("高风险专业建议")
                .doesNotContain("暂不支持直接比较");
    }

    @Test
    void generalComparisonNearestReachableBelowCapacityUsesFixedBoundary() {
        assertComparisonBoundary(policy.apply(comparisonProposal(2, 9)));
    }

    @Test
    void nineteenPairsCannotBeRepresentedByAValidGeneralComparisonGoal() {
        assertThatThrownBy(() -> comparisonProposal(1, 19))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("general comparison requires two to five subjects");
    }

    @Test
    void generalComparisonAtCapacityUsesFixedBoundary() {
        assertComparisonBoundary(policy.apply(comparisonProposal(4, 5)));
    }

    @Test
    void oversizedGeneralComparisonUsesTheSameFixedBoundary() {
        assertComparisonBoundary(policy.apply(comparisonProposal(3, 7)));
    }

    private void assertComparisonBoundary(ResolvedGoalSet result) {
        assertThat(result.getKind()).isEqualTo(ResolvedGoalSet.Kind.BOUNDARY);
        assertThat(result.getMessage()).contains(
                "当前暂不支持直接比较；请分别询问这些概念。");
    }

    private UserGoalProposal comparisonProposal(int subjects, int dimensions) {
        return comparisonProposal(
                subjects, dimensions,
                GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION);
    }

    private UserGoalProposal comparisonProposal(
            int subjects, int dimensions,
            GoalKnowledgeRequirement requirement) {
        java.util.List<UserGoalProposal.InputAnchor> anchors = new java.util.ArrayList<>();
        for (int index = 0; index < subjects; index++) {
            anchors.add(new UserGoalProposal.InputAnchor("主体" + index, 0));
        }
        java.util.Set<String> names = new java.util.HashSet<>();
        for (int index = 0; index < dimensions; index++) {
            names.add("DIM_" + (char) ('A' + index));
        }
        return new UserGoalProposal(java.util.List.of(new UserGoalProposal.ProposedGoal(
                "general-comparison", GoalKind.GENERAL_COMPARISON,
                new UserGoalProposal.InputAnchor("比较", 0), java.util.List.of(),
                java.util.Set.of(GoalRequestedOutput.COMPARISON),
                requirement,
                new UserGoalProposal.GeneralComparisonParameters(anchors, names))));
    }
}
