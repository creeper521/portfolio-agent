package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.ConfidenceField;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.ConfidenceLevel;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.ConfidenceOrigin;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.RequestedOutput;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectResolutionSource;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskDependencyType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.DependencyOrigin;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.SemanticTurnPlan;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.TaskConfidence;
import com.portfolio.agent.answer.routing.domain.TaskDependency;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TurnDecisionPolicyTest {

    @Test
    void policyEmitsAllNineApprovedConfirmationTriggers() {
        List<SubjectReference> subjects = List.of(
                subject("project-a"), subject("project-b"), subject("project-c"), subject("project-d"));
        SemanticTask fact = fact("task-01", subjects.get(0), TaskConfidence.highRule());
        SemanticTask comparison = SemanticTask.create(
                "task-02", SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_COMPARE,
                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO, "比较公开项目",
                new SemanticTaskParameters.PortfolioCompare(subjects.subList(0, 3), Set.of("ARCHITECTURE"), "GUEST"),
                Set.of(RequestedOutput.SUMMARY, RequestedOutput.COMPARISON, RequestedOutput.DETAILED),
                new TaskConfidence(ConfidenceLevel.MEDIUM, Map.of(ConfidenceField.SUBJECTS, ConfidenceLevel.MEDIUM),
                        ConfidenceOrigin.RULE),
                subjects.subList(0, 3));
        SemanticTask recommendation = SemanticTask.create(
                "task-03", SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_RECOMMEND,
                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO, "给出岗位推荐",
                new SemanticTaskParameters.PortfolioRecommend(subjects, "BACKEND_ENGINEERING", Set.of("JAVA"),
                        "岗位匹配", 2, "GUEST"),
                Set.of(RequestedOutput.RECOMMENDATION), TaskConfidence.highRule(), subjects);
        SemanticTask general = SemanticTask.create(
                "task-04", SemanticRoutingTypes.SemanticTaskType.GENERAL_EXPLANATION,
                SemanticRoutingTypes.TaskSourceDomain.GENERAL, "解释通用概念",
                new SemanticTaskParameters.GeneralExplanation("通用主题", "STANDARD", "GUEST"),
                Set.of(RequestedOutput.SUMMARY), TaskConfidence.highRule(), List.of());

        Set<SemanticTurnPlan.ConfirmationTrigger> triggers = new SemanticRoutingPolicy().confirmationTriggers(
                List.of(fact, comparison, recommendation, general),
                List.of(new TaskDependency("task-01", "task-02", TaskDependencyType.ORDER_AFTER,
                        DependencyOrigin.COMPILER_INFERRED)),
                SemanticRoutingPolicy.DecisionFacts.of(true, true, true));

        assertThat(triggers).containsExactlyInAnyOrder(
                SemanticTurnPlan.ConfirmationTrigger.TASK_COUNT_REQUIRES_CONFIRMATION,
                SemanticTurnPlan.ConfirmationTrigger.MEDIUM_CONFIDENCE_FIELD,
                SemanticTurnPlan.ConfirmationTrigger.MIXED_SOURCE_DOMAINS,
                SemanticTurnPlan.ConfirmationTrigger.INFERRED_DEPENDENCY,
                SemanticTurnPlan.ConfirmationTrigger.BROAD_SUBJECT_SCOPE,
                SemanticTurnPlan.ConfirmationTrigger.LARGE_OUTPUT_SCOPE,
                SemanticTurnPlan.ConfirmationTrigger.PARTIAL_EXECUTION,
                SemanticTurnPlan.ConfirmationTrigger.ORDER_ADJUSTED,
                SemanticTurnPlan.ConfirmationTrigger.NODE_CAPABILITY_BOUNDARY);
    }

    @Test
    void validSimplePlanIsReadyWithEveryTaskSelected() {
        SemanticTask task = fact("task-01", subject("project-a"), TaskConfidence.highRule());
        SemanticTurnPlan candidate = new SemanticTurnPlan(
                "plan-01", "content-v1", SemanticTurnPlan.PlanSource.RULE, List.of(task), List.of(), List.of(),
                Set.of(RequestedOutput.SUMMARY), SemanticTurnPlan.PlanConfirmationPolicy.noConfirmation());
        PlanValidationResult validation = new SemanticPlanValidator(new PlanFingerprintService())
                .validate(candidate, "stp-v1");

        SemanticTurnDecision decision = new TurnDecisionPolicy().decide(validation, null);

        assertThat(decision.getDisposition()).isEqualTo(SemanticTurnDecision.Disposition.READY);
        assertThat(decision.getExecutionSelection()).hasValueSatisfying(selection ->
                assertThat(selection.getExecutableTaskIds()).containsExactly("task-01"));
    }

    @Test
    void largeOutputScopeCountsDistinctOutputsAcrossTheTurn() {
        SubjectReference project = subject("project-a");
        List<SemanticTask> tasks = List.of(
                factWithOutput("task-01", project, RequestedOutput.SUMMARY),
                factWithOutput("task-02", project, RequestedOutput.EVIDENCE),
                factWithOutput("task-03", project, RequestedOutput.NEXT_STEPS));

        Set<SemanticTurnPlan.ConfirmationTrigger> triggers = new SemanticRoutingPolicy().confirmationTriggers(
                tasks, List.of(), SemanticRoutingPolicy.DecisionFacts.of(false, false, false));

        assertThat(triggers).contains(SemanticTurnPlan.ConfirmationTrigger.LARGE_OUTPUT_SCOPE);
    }

    private SemanticTask fact(String taskId, SubjectReference subject, TaskConfidence confidence) {
        return SemanticTask.create(
                taskId, SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT,
                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO, "介绍公开项目",
                new SemanticTaskParameters.PortfolioFact(subject, Set.of("OVERVIEW"), "GUEST"),
                Set.of(RequestedOutput.SUMMARY), confidence, List.of(subject));
    }

    private SemanticTask factWithOutput(
            String taskId, SubjectReference subject, RequestedOutput output) {
        return SemanticTask.create(
                taskId, SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT,
                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO, "介绍公开项目",
                new SemanticTaskParameters.PortfolioFact(subject, Set.of("OVERVIEW"), "GUEST"),
                Set.of(output), TaskConfidence.highRule(), List.of(subject));
    }

    private SubjectReference subject(String id) {
        return new SubjectReference(SubjectType.PROJECT, id, SubjectResolutionSource.EXPLICIT_REFERENCE, "content-v1");
    }
}
