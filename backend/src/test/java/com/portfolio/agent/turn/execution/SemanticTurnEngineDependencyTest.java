package com.portfolio.agent.turn.execution;

import com.portfolio.agent.turn.planning.GoalInterpretationInput;
import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.GoalKnowledgeRequirement;
import com.portfolio.agent.turn.planning.GoalRequestedOutput;
import com.portfolio.agent.turn.planning.GoalResolutionContext;
import com.portfolio.agent.turn.planning.GoalSubjectReference;
import com.portfolio.agent.turn.planning.PlanCompilationResult;
import com.portfolio.agent.turn.planning.SemanticPlanCompiler;
import com.portfolio.agent.turn.planning.SemanticPlanValidator;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import com.portfolio.agent.turn.planning.ValidatedSemanticTurnPlan;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticTurnEngineDependencyTest {
    @Test
    void downstreamReceivesProducedSemanticResultsOnly() {
        AtomicInteger received = new AtomicInteger(-1);
        List<SemanticTaskExecutor> executors = List.of(
                executor(SemanticTask.SourceDomain.GENERAL, false),
                executor(SemanticTask.SourceDomain.PORTFOLIO, true),
                new SemanticTaskExecutor() {
                    @Override public SemanticTask.SourceDomain getSourceDomain() {
                        return SemanticTask.SourceDomain.SYNTHESIS;
                    }
                    @Override public TaskExecutionResult execute(TaskExecutionContext context) {
                        received.set(context.getDependencyResults().size());
                        return TaskExecutionResult.full(ExecutionTestPlanFactory.artifact());
                    }
                });
        try (java.util.concurrent.ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            SemanticTurnOutcome outcome = new SemanticTurnEngine(executors, pool, 3).execute(
                    crossDomainPlan(), TurnDeadline.after(Duration.ofSeconds(2), Clock.systemUTC()),
                    new CancellationSignal(), false);
            assertThat(received.get()).isEqualTo(1);
            assertThat(outcome.getTaskOutcomes().get(2).getTerminal())
                    .isInstanceOf(TaskOutcome.Produced.class);
        }
    }

    @Test
    void downstreamIsBlockedWhenNoInboundTaskProducesAResult() {
        AtomicInteger synthesisCalls = new AtomicInteger();
        List<SemanticTaskExecutor> executors = List.of(
                executor(SemanticTask.SourceDomain.GENERAL, true),
                executor(SemanticTask.SourceDomain.PORTFOLIO, true),
                new SemanticTaskExecutor() {
                    @Override public SemanticTask.SourceDomain getSourceDomain() {
                        return SemanticTask.SourceDomain.SYNTHESIS;
                    }
                    @Override public TaskExecutionResult execute(TaskExecutionContext context) {
                        synthesisCalls.incrementAndGet();
                        return TaskExecutionResult.full(ExecutionTestPlanFactory.artifact());
                    }
                });
        try (java.util.concurrent.ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            SemanticTurnOutcome outcome = new SemanticTurnEngine(executors, pool, 3).execute(
                    crossDomainPlan(), TurnDeadline.after(Duration.ofSeconds(2), Clock.systemUTC()),
                    new CancellationSignal(), false);
            assertThat(synthesisCalls.get()).isZero();
            assertThat(outcome.getTaskOutcomes().get(2).getTerminal())
                    .isInstanceOf(TaskOutcome.Blocked.class);
            assertThat(outcome.getGoalCoverage().getFirst().getCoverage())
                    .isEqualTo(GoalCoverage.Coverage.NONE);
        }
    }

    private SemanticTaskExecutor executor(SemanticTask.SourceDomain domain, boolean noResult) {
        return new SemanticTaskExecutor() {
            @Override public SemanticTask.SourceDomain getSourceDomain() { return domain; }
            @Override public TaskExecutionResult execute(TaskExecutionContext context) {
                if (noResult) throw new TaskTerminalException(
                        TaskTerminalException.Kind.NO_RESULT, TaskTerminalReason.NO_SUPPORTED_RESULT);
                return TaskExecutionResult.full(ExecutionTestPlanFactory.artifact());
            }
        };
    }

    private ValidatedSemanticTurnPlan crossDomainPlan() {
        UserGoalProposal.InputAnchor anchor = new UserGoalProposal.InputAnchor("幂等", 0);
        UserGoalProposal.ProposedGoal goal = new UserGoalProposal.ProposedGoal(
                "apply-concept", GoalKind.APPLY_GENERAL_CONCEPT_TO_PORTFOLIO, anchor,
                List.of(new GoalSubjectReference(
                        GoalSubjectReference.Kind.PROJECT, "sql-audit",
                        GoalSubjectReference.Basis.EXPLICIT_INPUT, anchor)),
                Set.of(GoalRequestedOutput.RELATION),
                GoalKnowledgeRequirement.PUBLIC_PORTFOLIO_EVIDENCE,
                new UserGoalProposal.ApplyConceptParameters(
                        anchor, UserGoalProposal.Facet.SOLUTION,
                        UserGoalProposal.Depth.STANDARD));
        GoalResolutionContext context = new GoalResolutionContext(
                List.of(new GoalInterpretationInput.PublicSubjectDescriptor(
                        GoalSubjectReference.Kind.PROJECT, "sql-audit", "SQL 审计项目")),
                Set.of(GoalKind.values()));
        PlanCompilationResult result = new SemanticPlanCompiler(new SemanticPlanValidator())
                .compile(new UserGoalProposal(List.of(goal)), "public-1", context);
        return result.getPlan().orElseThrow();
    }
}
