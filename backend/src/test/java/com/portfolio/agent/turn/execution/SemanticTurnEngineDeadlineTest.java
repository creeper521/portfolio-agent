package com.portfolio.agent.turn.execution;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticTurnEngineDeadlineTest {
    @Test
    void expiredDeadlineStartsNoTaskAndSettlesAsTimedOut() {
        AtomicInteger calls = new AtomicInteger();
        SemanticTaskExecutor executor = new SemanticTaskExecutor() {
            @Override public com.portfolio.agent.turn.planning.SemanticTask.SourceDomain getSourceDomain() {
                return com.portfolio.agent.turn.planning.SemanticTask.SourceDomain.GENERAL;
            }
            @Override public TaskExecutionResult execute(TaskExecutionContext context) {
                calls.incrementAndGet();
                return TaskExecutionResult.full(ExecutionTestPlanFactory.artifact());
            }
        };
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC);
        try (java.util.concurrent.ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            SemanticTurnOutcome outcome = new SemanticTurnEngine(List.of(executor), pool, 1).execute(
                    ExecutionTestPlanFactory.oneGeneralTask(),
                    new TurnDeadline(clock.instant(), clock), new CancellationSignal(), false);
            assertThat(calls.get()).isZero();
            assertThat(outcome.getTaskOutcomes().getFirst().getTerminal())
                    .isInstanceOf(TaskOutcome.TimedOut.class);
        }
    }

    @Test
    void deadlinePreservesCompletedBranchAndTimesOutOnlyLateBranch() throws Exception {
        java.util.concurrent.CountDownLatch lateStarted = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch releaseLate = new java.util.concurrent.CountDownLatch(1);
        SemanticTaskExecutor executor = new SemanticTaskExecutor() {
            @Override public com.portfolio.agent.turn.planning.SemanticTask.SourceDomain getSourceDomain() {
                return com.portfolio.agent.turn.planning.SemanticTask.SourceDomain.GENERAL;
            }
            @Override public TaskExecutionResult execute(TaskExecutionContext context) {
                if (context.getTask().getTaskId().equals("task-second")) {
                    lateStarted.countDown();
                    boolean released = false;
                    while (!released) {
                        try { releaseLate.await(); released = true; }
                        catch (InterruptedException ignored) { }
                    }
                }
                return TaskExecutionResult.full(ExecutionTestPlanFactory.artifact());
            }
        };
        try (java.util.concurrent.ExecutorService taskPool = Executors.newVirtualThreadPerTaskExecutor();
             java.util.concurrent.ExecutorService caller = Executors.newSingleThreadExecutor()) {
            SemanticTurnEngine engine = new SemanticTurnEngine(List.of(executor), taskPool, 2);
            java.util.concurrent.Future<SemanticTurnOutcome> future = caller.submit(() -> engine.execute(
                    ExecutionTestPlanFactory.twoGeneralTasks(),
                    TurnDeadline.after(java.time.Duration.ofMillis(50), Clock.systemUTC()),
                    new CancellationSignal(), false));
            assertThat(lateStarted.await(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            SemanticTurnOutcome outcome = future.get(1, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(outcome.getTaskOutcomes().get(0).getTerminal())
                    .isInstanceOf(TaskOutcome.Produced.class);
            assertThat(outcome.getTaskOutcomes().get(1).getTerminal())
                    .isInstanceOf(TaskOutcome.TimedOut.class);
            assertThat(outcome.getGoalCoverage()).extracting(GoalCoverage::getCoverage)
                    .containsExactly(GoalCoverage.Coverage.FULL, GoalCoverage.Coverage.NONE);
            releaseLate.countDown();
        }
    }
}
