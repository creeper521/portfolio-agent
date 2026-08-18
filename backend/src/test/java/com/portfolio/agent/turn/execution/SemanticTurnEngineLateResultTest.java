package com.portfolio.agent.turn.execution;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticTurnEngineLateResultTest {
    @Test
    void completionAfterDeadlineSettlementCannotReplaceTimedOutTerminal() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        SemanticTaskExecutor lateExecutor = new SemanticTaskExecutor() {
            @Override public com.portfolio.agent.turn.planning.SemanticTask.SourceDomain getSourceDomain() {
                return com.portfolio.agent.turn.planning.SemanticTask.SourceDomain.GENERAL;
            }
            @Override public TaskExecutionResult execute(TaskExecutionContext context) {
                started.countDown();
                boolean released = false;
                while (!released) {
                    try {
                        release.await();
                        released = true;
                    } catch (InterruptedException ignored) {
                        // Deliberately emulate a provider that completes after cancellation.
                    }
                }
                finished.countDown();
                return TaskExecutionResult.full(ExecutionTestPlanFactory.artifact());
            }
        };
        try (java.util.concurrent.ExecutorService taskPool = Executors.newVirtualThreadPerTaskExecutor();
             java.util.concurrent.ExecutorService caller = Executors.newSingleThreadExecutor()) {
            SemanticTurnEngine engine = new SemanticTurnEngine(List.of(lateExecutor), taskPool, 1);
            java.util.concurrent.Future<SemanticTurnOutcome> execution = caller.submit(() -> engine.execute(
                    ExecutionTestPlanFactory.oneGeneralTask(),
                    TurnDeadline.after(Duration.ofMillis(40), Clock.systemUTC()),
                    new CancellationSignal(), List.of(), false));

            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            SemanticTurnOutcome outcome = execution.get(1, TimeUnit.SECONDS);
            assertThat(outcome.getTaskOutcomes().getFirst().getTerminal())
                    .isInstanceOf(TaskOutcome.TimedOut.class);
            release.countDown();
            assertThat(finished.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(outcome.getTaskOutcomes().getFirst().getTerminal())
                    .isInstanceOf(TaskOutcome.TimedOut.class);
            assertThat(outcome.getGoalCoverage().getFirst().getCoverage())
                    .isEqualTo(GoalCoverage.Coverage.NONE);
        }
    }
}
