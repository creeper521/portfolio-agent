package com.portfolio.agent.turn.execution;

import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.SemanticPlanValidator;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.SemanticTurnPlan;
import com.portfolio.agent.turn.planning.UserGoal;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticTurnEngineStableOrderTest {
    @Test
    void reversedFutureCompletionStillCommitsPlanOrder() {
        CountDownLatch secondCompleted = new CountDownLatch(1);
        SemanticTask first = ExecutionTestPlanFactory.generalTask("task-first", "first");
        SemanticTask second = ExecutionTestPlanFactory.generalTask("task-second", "second");
        SemanticTurnPlan plan = new SemanticTurnPlan("public-1", List.of(
                goal("goal-first", first), goal("goal-second", second)),
                List.of(first, second), List.of());
        SemanticTaskExecutor executor = new SemanticTaskExecutor() {
            @Override public SemanticTask.SourceDomain getSourceDomain() {
                return SemanticTask.SourceDomain.GENERAL;
            }
            @Override public TaskExecutionResult execute(TaskExecutionContext context) {
                try {
                    if (context.getTask().getTaskId().equals("task-first")) {
                        secondCompleted.await();
                    } else {
                        secondCompleted.countDown();
                    }
                    return TaskExecutionResult.full(ExecutionTestPlanFactory.artifact());
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(failure);
                }
            }
        };
        try (java.util.concurrent.ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            SemanticTurnOutcome outcome = new SemanticTurnEngine(List.of(executor), pool, 2).execute(
                    new SemanticPlanValidator().validate(plan),
                    TurnDeadline.after(Duration.ofSeconds(1), Clock.systemUTC()),
                    new CancellationSignal(), List.of(), false);
            assertThat(outcome.getTaskOutcomes()).extracting(TaskOutcome::getTaskId)
                    .containsExactly("task-first", "task-second");
        }
    }

    private UserGoal goal(String id, SemanticTask task) {
        return new UserGoal(id, id, GoalKind.GENERAL_EXPLANATION,
                List.of(), Set.of(), task.getTaskId());
    }
}
