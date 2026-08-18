package com.portfolio.agent.turn.execution;

import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.ValidatedSemanticTurnPlan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class SemanticTurnEngine {
    private final Map<SemanticTask.SourceDomain, SemanticTaskExecutor> executors;
    private final ExecutorService executorService;
    private final int maxParallelTasks;
    private final ReadySetScheduler scheduler = new ReadySetScheduler();
    private final GoalCoverageProjector coverageProjector = new GoalCoverageProjector();

    public SemanticTurnEngine(
            List<SemanticTaskExecutor> executors,
            ExecutorService executorService,
            int maxParallelTasks) {
        this.executors = index(executors);
        this.executorService = Objects.requireNonNull(executorService, "executorService");
        if (maxParallelTasks < 1 || maxParallelTasks > 32) {
            throw new IllegalArgumentException("maxParallelTasks is invalid");
        }
        this.maxParallelTasks = maxParallelTasks;
    }

    public SemanticTurnOutcome execute(
            ValidatedSemanticTurnPlan validatedPlan,
            TurnDeadline deadline,
            CancellationSignal cancellation,
            boolean presetRequest) {
        Objects.requireNonNull(validatedPlan, "validatedPlan");
        com.portfolio.agent.turn.planning.SemanticTurnPlan plan = validatedPlan.getPlan();
        Map<String, TaskOutcome> outcomes = new LinkedHashMap<>();
        String expressionTaskId = presetRequest ? null : plan.getTasks().stream()
                .filter(task -> task.getType() == SemanticTask.Type.PORTFOLIO_FACT)
                .map(SemanticTask::getTaskId).findFirst().orElse(null);
        while (outcomes.size() < plan.getTasks().size()) {
            if (cancellation.isCancelled() || deadline.isExpired()) break;
            List<SemanticTask> ready = scheduler.ready(plan, outcomes);
            if (ready.isEmpty()) throw new IllegalStateException("validated plan made no scheduling progress");
            List<SemanticTask> runnable = new ArrayList<>();
            for (SemanticTask task : ready) {
                if (scheduler.blockedByDependencies(plan, task.getTaskId(), outcomes)) {
                    outcomes.put(task.getTaskId(), new TaskOutcome(task.getTaskId(),
                            new TaskOutcome.Blocked(TaskTerminalReason.DEPENDENCY_UNAVAILABLE)));
                } else {
                    runnable.add(task);
                }
            }
            for (int offset = 0; offset < runnable.size(); offset += maxParallelTasks) {
                int end = Math.min(runnable.size(), offset + maxParallelTasks);
                if (!executeBatch(runnable.subList(offset, end), plan, outcomes, deadline,
                        cancellation, expressionTaskId, presetRequest)) break;
            }
        }
        for (SemanticTask task : plan.getTasks()) {
            if (outcomes.containsKey(task.getTaskId())) continue;
            TaskOutcome.Terminal terminal = cancellation.isCancelled()
                    ? new TaskOutcome.Cancelled() : new TaskOutcome.TimedOut();
            outcomes.put(task.getTaskId(), new TaskOutcome(task.getTaskId(), terminal));
        }
        List<TaskOutcome> ordered = plan.getTasks().stream()
                .map(task -> outcomes.get(task.getTaskId())).toList();
        return new SemanticTurnOutcome(ordered, coverageProjector.project(plan, outcomes));
    }

    private boolean executeBatch(
            List<SemanticTask> tasks,
            com.portfolio.agent.turn.planning.SemanticTurnPlan plan,
            Map<String, TaskOutcome> outcomes,
            TurnDeadline deadline,
            CancellationSignal cancellation,
            String expressionTaskId,
            boolean presetRequest) {
        CompletionService<TaskOutcome> completions = new ExecutorCompletionService<>(executorService);
        Map<Future<TaskOutcome>, String> taskIds = new HashMap<>();
        LateResultGate gate = new LateResultGate();
        for (SemanticTask task : tasks) {
            Future<TaskOutcome> future = completions.submit(() -> executeTask(task,
                    scheduler.dependencyResults(plan, task.getTaskId(), outcomes),
                    plan.getContentReleaseId(), deadline, cancellation,
                    task.getTaskId().equals(expressionTaskId), presetRequest));
            taskIds.put(future, task.getTaskId());
        }
        int remaining = tasks.size();
        try {
            while (remaining > 0 && !cancellation.isCancelled() && !deadline.isExpired()) {
                Future<TaskOutcome> future = completions.poll(
                        Math.min(10L, Math.max(1L, deadline.remainingMillis())),
                        TimeUnit.MILLISECONDS);
                if (future == null) continue;
                TaskOutcome outcome = future.get();
                if (gate.tryAccept()) outcomes.put(outcome.getTaskId(), outcome);
                remaining--;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            cancellation.cancel();
        } catch (ExecutionException failure) {
            throw new IllegalStateException("executor wrapper failed", failure.getCause());
        } finally {
            gate.settle();
            for (Future<TaskOutcome> future : taskIds.keySet()) {
                if (!future.isDone()) future.cancel(true);
            }
        }
        return remaining == 0;
    }

    private TaskOutcome executeTask(
            SemanticTask task, List<TaskSemanticResult> dependencyResults,
            String contentReleaseId, TurnDeadline deadline, CancellationSignal cancellation,
            boolean modelExpressionAllowed, boolean presetRequest) {
        SemanticTaskExecutor executor = executors.get(task.getSourceDomain());
        if (executor == null) return new TaskOutcome(task.getTaskId(),
                new TaskOutcome.Failed(TaskTerminalReason.CAPABILITY_UNAVAILABLE));
        try {
            TaskExecutionResult result = executor.execute(new TaskExecutionContext(
                    task, dependencyResults, contentReleaseId, deadline, cancellation,
                    modelExpressionAllowed, presetRequest));
            return new TaskOutcome(task.getTaskId(),
                    new TaskOutcome.Produced(result.getArtifact(), result.getFulfillment()));
        } catch (TaskTerminalException terminal) {
            TaskOutcome.Terminal value = switch (terminal.getKind()) {
                case NO_RESULT -> new TaskOutcome.NoResult(terminal.getReason());
                case REJECTED -> new TaskOutcome.Rejected(terminal.getReason());
                case FAILED -> new TaskOutcome.Failed(terminal.getReason());
            };
            return new TaskOutcome(task.getTaskId(), value);
        } catch (RuntimeException failure) {
            return new TaskOutcome(task.getTaskId(),
                    new TaskOutcome.Failed(TaskTerminalReason.EXECUTION_FAILED));
        }
    }

    private Map<SemanticTask.SourceDomain, SemanticTaskExecutor> index(List<SemanticTaskExecutor> values) {
        Map<SemanticTask.SourceDomain, SemanticTaskExecutor> indexed = new HashMap<>();
        for (SemanticTaskExecutor value : List.copyOf(values)) {
            if (indexed.put(value.getSourceDomain(), value) != null) {
                throw new IllegalArgumentException("one executor per source domain is required");
            }
        }
        return Map.copyOf(indexed);
    }
}
