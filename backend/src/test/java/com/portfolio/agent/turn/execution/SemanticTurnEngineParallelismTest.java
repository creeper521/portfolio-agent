package com.portfolio.agent.turn.execution;

import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.SemanticPlanValidator;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.SemanticTaskParameters;
import com.portfolio.agent.turn.planning.SemanticTurnPlan;
import com.portfolio.agent.turn.planning.UserGoal;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticTurnEngineParallelismTest {
    @Test
    void startsIndependentReadyNodesInParallelButCommitsPlanOrder() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger inflight = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        ResolvedModelExecution selected = ResolvedModelExecution.none();
        ConcurrentLinkedQueue<ResolvedModelExecution> received =
                new ConcurrentLinkedQueue<>();
        SemanticTaskExecutor executor = new SemanticTaskExecutor() {
            @Override public SemanticTask.SourceDomain getSourceDomain() {
                return SemanticTask.SourceDomain.GENERAL;
            }
            @Override public TaskExecutionResult execute(TaskExecutionContext context) {
                received.add(context.getModelExecution());
                int current = inflight.incrementAndGet();
                maximum.accumulateAndGet(current, Math::max);
                try {
                    barrier.await(1, TimeUnit.SECONDS);
                    return TaskExecutionResult.full(artifact(context.getTask().getTaskId()));
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                } finally {
                    inflight.decrementAndGet();
                }
            }
        };
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            SemanticTurnEngine engine = new SemanticTurnEngine(List.of(executor), pool, 2);
            SemanticTurnOutcome outcome = engine.execute(
                    new SemanticPlanValidator().validate(plan()),
                    TurnDeadline.after(Duration.ofSeconds(2), Clock.systemUTC()),
                    new CancellationSignal(), false, selected);

            assertThat(maximum.get()).isEqualTo(2);
            assertThat(outcome.getTaskOutcomes()).extracting(TaskOutcome::getTaskId)
                    .containsExactly("task-first", "task-second");
            assertThat(outcome.getGoalCoverage()).extracting(GoalCoverage::getCoverage)
                    .containsExactly(GoalCoverage.Coverage.FULL, GoalCoverage.Coverage.FULL);
            assertThat(received).hasSize(2).allMatch(value -> value == selected);
        }
    }

    private SemanticTurnPlan plan() {
        SemanticTask first = task("task-first", "first");
        SemanticTask second = task("task-second", "second");
        return new SemanticTurnPlan("public-1", List.of(
                goal("goal-first", "task-first"), goal("goal-second", "task-second")),
                List.of(first, second), List.of());
    }

    private UserGoal goal(String id, String taskId) {
        return new UserGoal(id, id, GoalKind.GENERAL_EXPLANATION,
                List.of(), Set.of(), taskId);
    }

    private SemanticTask task(String id, String text) {
        UserGoalProposal.InputAnchor anchor = new UserGoalProposal.InputAnchor(text, 0);
        return SemanticTask.of(id, SemanticTask.Type.GENERAL_EXPLANATION,
                new SemanticTaskParameters(GoalKind.GENERAL_EXPLANATION,
                        new UserGoalProposal.GeneralExplanationParameters(
                                anchor, UserGoalProposal.Depth.STANDARD), List.of()));
    }

    private TaskArtifact artifact(String value) {
        return new TaskArtifact(new Result(value), new Presentation(), TaskProvenance.none());
    }

    private static final class Result implements TaskSemanticResult {
        private final String value;
        private Result(String value) { this.value = value; }
        private String getValue() { return value; }
    }
    private static final class Presentation implements TaskPresentation { }
}
