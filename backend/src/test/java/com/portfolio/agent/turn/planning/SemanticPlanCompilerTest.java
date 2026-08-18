package com.portfolio.agent.turn.planning;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticPlanCompilerTest {

    private final SemanticPlanCompiler compiler = new SemanticPlanCompiler(new SemanticPlanValidator());

    @Test
    void compilesIndependentGoalsWithoutOrderEdges() {
        UserGoalProposal proposal = new UserGoalProposal(List.of(portfolioFact(), generalExplanation()));

        PlanCompilationResult result = compiler.compile(proposal, "2026-08-05.1", context());

        assertThat(result.getKind()).isEqualTo(PlanCompilationResult.Kind.COMPILED);
        SemanticTurnPlan plan = result.getPlan().orElseThrow().getPlan();
        assertThat(plan.getUserGoals()).hasSize(2);
        assertThat(plan.getTasks()).hasSize(2);
        assertThat(plan.getDependencies()).isEmpty();
        assertThat(plan.getUserGoals()).extracting(UserGoal::getFulfillmentTaskId)
                .containsExactly("task-goal-1", "task-goal-2");
    }

    @Test
    void compilesCrossDomainGoalAsExactlyOneGeneralAndPortfolioFanIn() {
        UserGoalProposal proposal = new UserGoalProposal(List.of(crossDomain()));

        SemanticTurnPlan plan = compiler.compile(proposal, "2026-08-05.1", context())
                .getPlan().orElseThrow().getPlan();

        assertThat(plan.getTasks()).extracting(SemanticTask::getType)
                .containsExactly(
                        SemanticTask.Type.GENERAL_EXPLANATION,
                        SemanticTask.Type.PORTFOLIO_FACT,
                        SemanticTask.Type.CROSS_DOMAIN_SYNTHESIS);
        assertThat(plan.getDependencies()).hasSize(2);
        assertThat(plan.getDependencies()).extracting(TaskDependency::getToTaskId)
                .containsOnly("task-goal-1");
        assertThat(plan.getUserGoals().get(0).getFulfillmentTaskId()).isEqualTo("task-goal-1");
    }

    @Test
    void nonPublicPortfolioSubjectRequiresClarification() {
        UserGoalProposal.ProposedGoal goal = new UserGoalProposal.ProposedGoal(
                "unknown-subject", GoalKind.PORTFOLIO_FACT,
                new UserGoalProposal.InputAnchor("未知项目", 0),
                List.of(new GoalSubjectReference(
                        GoalSubjectReference.Kind.PROJECT, "unknown-project",
                        GoalSubjectReference.Basis.EXPLICIT_INPUT,
                        new UserGoalProposal.InputAnchor("未知项目", 0))),
                Set.of(GoalRequestedOutput.OVERVIEW),
                GoalKnowledgeRequirement.PUBLIC_PORTFOLIO_EVIDENCE,
                new UserGoalProposal.PortfolioFactParameters(
                        Set.of(UserGoalProposal.Facet.OVERVIEW)));

        PlanCompilationResult result = compiler.compile(
                new UserGoalProposal(List.of(goal)), "2026-08-05.1", context());

        assertThat(result.getKind()).isEqualTo(PlanCompilationResult.Kind.CLARIFICATION_REQUIRED);
    }

    static UserGoalProposal.ProposedGoal portfolioFact() {
        UserGoalProposal.InputAnchor anchor = new UserGoalProposal.InputAnchor("SQL 审计项目", 0);
        return new UserGoalProposal.ProposedGoal(
                "portfolio-fact", GoalKind.PORTFOLIO_FACT, anchor,
                List.of(new GoalSubjectReference(
                        GoalSubjectReference.Kind.PROJECT, "sql-audit",
                        GoalSubjectReference.Basis.EXPLICIT_INPUT, anchor)),
                Set.of(GoalRequestedOutput.OVERVIEW),
                GoalKnowledgeRequirement.PUBLIC_PORTFOLIO_EVIDENCE,
                new UserGoalProposal.PortfolioFactParameters(
                        Set.of(UserGoalProposal.Facet.OVERVIEW)));
    }

    static UserGoalProposal.ProposedGoal generalExplanation() {
        UserGoalProposal.InputAnchor anchor = new UserGoalProposal.InputAnchor("幂等", 0);
        return new UserGoalProposal.ProposedGoal(
                "general", GoalKind.GENERAL_EXPLANATION, anchor, List.of(),
                Set.of(GoalRequestedOutput.EXPLANATION),
                GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION,
                new UserGoalProposal.GeneralExplanationParameters(
                        anchor, UserGoalProposal.Depth.STANDARD));
    }

    static UserGoalProposal.ProposedGoal crossDomain() {
        UserGoalProposal.InputAnchor anchor = new UserGoalProposal.InputAnchor("幂等", 0);
        return new UserGoalProposal.ProposedGoal(
                "apply-concept", GoalKind.APPLY_GENERAL_CONCEPT_TO_PORTFOLIO, anchor,
                SemanticPlanCompilerTest.portfolioFact().getSubjectCandidates(),
                Set.of(GoalRequestedOutput.RELATION),
                GoalKnowledgeRequirement.PUBLIC_PORTFOLIO_EVIDENCE,
                new UserGoalProposal.ApplyConceptParameters(
                        anchor, UserGoalProposal.Facet.SOLUTION));
    }

    static GoalResolutionContext context() {
        return new GoalResolutionContext(
                List.of(new GoalInterpretationInput.PublicSubjectDescriptor(
                        GoalSubjectReference.Kind.PROJECT, "sql-audit", "SQL 审计项目")),
                Set.of(GoalKind.values()));
    }
}
