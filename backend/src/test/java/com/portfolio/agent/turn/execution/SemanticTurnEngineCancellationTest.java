package com.portfolio.agent.turn.execution;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticTurnEngineCancellationTest {
    @Test
    void preCancelledTurnStartsNoTaskAndSettlesOnceAsCancelled() {
        AtomicInteger calls = new AtomicInteger();
        SemanticTaskExecutor executor = new TestExecutor(calls);
        CancellationSignal cancellation = new CancellationSignal();
        assertThat(cancellation.cancel()).isTrue();
        assertThat(cancellation.cancel()).isFalse();
        try (java.util.concurrent.ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            SemanticTurnOutcome outcome = new SemanticTurnEngine(List.of(executor), pool, 1).execute(
                    ExecutionTestPlanFactory.oneGeneralTask(),
                    TurnDeadline.after(Duration.ofSeconds(1), Clock.systemUTC()),
                    cancellation, false);
            assertThat(calls.get()).isZero();
            assertThat(outcome.getTaskOutcomes().getFirst().getTerminal())
                    .isInstanceOf(TaskOutcome.Cancelled.class);
            assertThat(outcome.getGoalCoverage().getFirst().getCoverage())
                    .isEqualTo(GoalCoverage.Coverage.NONE);
        }
    }

    @Test
    void runningCancellationSettlesWithoutWaitingForDeadlineAndLateCompletionIsIgnored()
            throws Exception {
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        SemanticTaskExecutor executor = new SemanticTaskExecutor() {
            @Override public com.portfolio.agent.turn.planning.SemanticTask.SourceDomain getSourceDomain() {
                return com.portfolio.agent.turn.planning.SemanticTask.SourceDomain.GENERAL;
            }
            @Override public TaskExecutionResult execute(TaskExecutionContext context) {
                started.countDown();
                boolean done = false;
                while (!done) {
                    try { release.await(); done = true; }
                    catch (InterruptedException ignored) { }
                }
                return TaskExecutionResult.full(ExecutionTestPlanFactory.artifact());
            }
        };
        CancellationSignal cancellation = new CancellationSignal();
        try (java.util.concurrent.ExecutorService taskPool = Executors.newVirtualThreadPerTaskExecutor();
             java.util.concurrent.ExecutorService caller = Executors.newSingleThreadExecutor()) {
            SemanticTurnEngine engine = new SemanticTurnEngine(List.of(executor), taskPool, 1);
            java.util.concurrent.Future<SemanticTurnOutcome> future = caller.submit(() -> engine.execute(
                    ExecutionTestPlanFactory.oneGeneralTask(),
                    TurnDeadline.after(Duration.ofSeconds(2), Clock.systemUTC()),
                    cancellation, false));
            assertThat(started.await(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            cancellation.cancel();
            SemanticTurnOutcome outcome = future.get(1, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(outcome.getTaskOutcomes().getFirst().getTerminal())
                    .isInstanceOf(TaskOutcome.Cancelled.class);
            release.countDown();
        }
    }

    private static final class TestExecutor implements SemanticTaskExecutor {
        private final AtomicInteger calls;
        private TestExecutor(AtomicInteger calls) { this.calls = calls; }
        @Override public com.portfolio.agent.turn.planning.SemanticTask.SourceDomain getSourceDomain() {
            return com.portfolio.agent.turn.planning.SemanticTask.SourceDomain.GENERAL;
        }
        @Override public TaskExecutionResult execute(TaskExecutionContext context) {
            calls.incrementAndGet();
            return TaskExecutionResult.full(ExecutionTestPlanFactory.artifact());
        }
    }
}
