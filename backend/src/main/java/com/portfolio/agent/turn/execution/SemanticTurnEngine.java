package com.portfolio.agent.turn.execution;

import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.infrastructure.model.SelectedModelFailureException;
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

/**
 * 语义执行引擎：Agent 2.0 管线 Execution 阶段的唯一驱动者。
 *
 * <p>输入 {@link ValidatedSemanticTurnPlan}（Planning 的产物），按依赖边以就绪批次
 * 并发调度任务，交由按 {@link SemanticTask.SourceDomain} 索引的 {@link SemanticTaskExecutor}
 * 执行，最终折叠为 {@link SemanticTurnOutcome}（全部任务终态 + Goal 覆盖度投影）。
 * 协作组件：{@link ReadySetScheduler} 计算就绪集、{@link LateResultGate} 拒绝迟到结果、
 * {@link GoalCoverageProjector} 投影覆盖度。
 *
 * <p>关键不变量：每个计划任务在返回前必然获得一个 {@link TaskOutcome} 终态，
 * 未及执行的任务按取消/超时补 Cancelled 或 TimedOut；单个 {@code maxParallelTasks}
 * 批次内共享同一 TurnDeadline 与 CancellationSignal；任务抛出的
 * {@code SelectedModelFailureException} 不被吞掉而是原样上抛（模型选择失败 fail-closed，
 * 整轮 Turn 终止而非降级继续）；任意未捕获的 RuntimeException 一律收敛为
 * FAILED(EXECUTION_FAILED) 终态，不向访客暴露内部异常。
 */
public final class SemanticTurnEngine {
    private final Map<SemanticTask.SourceDomain, SemanticTaskExecutor> executors;
    private final ExecutorService executorService;
    private final int maxParallelTasks;
    private final ReadySetScheduler scheduler = new ReadySetScheduler();
    private final GoalCoverageProjector coverageProjector = new GoalCoverageProjector();

    /** 每个 SourceDomain 只允许一个 Executor，重复注册抛 IllegalArgumentException；maxParallelTasks 须在 1..32。 */
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

    /** 兼容无模型执行的旧入口：以 {@code ResolvedModelExecution.none()} 委托给完整入口。 */
    SemanticTurnOutcome execute(
            ValidatedSemanticTurnPlan validatedPlan,
            TurnDeadline deadline,
            CancellationSignal cancellation,
            boolean presetRequest) {
        return execute(
                validatedPlan, deadline, cancellation, presetRequest,
                ResolvedModelExecution.none());
    }

    /**
     * 执行整轮计划的主循环：反复计算就绪集 → 剥离被依赖阻塞的任务 → 按批并发执行，
     * 直到全部任务获得终态、或取消/超时提前退出。
     *
     * <p>非 presetRequest 时仅第一个 PORTFOLIO_FACT 任务被授予模型表达权
     * （{@code modelExpressionAllowed}），防止多个任务争用同一次模型执行。
     * 提前退出后，尚无终态的任务统一补 Cancelled（已取消）或 TimedOut（超时）。
     *
     * @param presetRequest 是否为预设请求（预设路径不授予模型表达权）
     * @param modelExecution Claim 后冻结的无凭证模型执行快照，所有任务共享
     * @throws IllegalStateException 已验证计划在无取消/超时下无法继续调度（计划缺陷，不可恢复）
     * @throws SelectedModelFailureException 任务内模型选择失败，原样上抛使整轮 fail-closed
     */
    public SemanticTurnOutcome execute(
            ValidatedSemanticTurnPlan validatedPlan,
            TurnDeadline deadline,
            CancellationSignal cancellation,
            boolean presetRequest,
            ResolvedModelExecution modelExecution) {
        Objects.requireNonNull(validatedPlan, "validatedPlan");
        Objects.requireNonNull(modelExecution, "modelExecution");
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
                        cancellation, expressionTaskId, presetRequest,
                        modelExecution)) break;
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

    /**
     * 并发执行一个就绪批次并收集结果，逐任务写入 outcomes。
     *
     * <p>以短周期 poll 轮询完成队列（每次至多 10ms），使取消与截止时间检查能及时生效；
     * 批内共享一个 {@link LateResultGate}，退出收集循环即关门，之后完成的结果被丢弃。
     * 收到中断时恢复中断标记并触发取消；finally 中对未完成任务执行
     * {@code future.cancel(true)} 传播取消，尽力回收线程。
     *
     * @return 批次是否全部任务都收集到了结果；false 表示因取消/超时/中断提前退出
     * @throws SelectedModelFailureException 任务内部模型选择失败时原样上抛
     * @throws IllegalStateException 执行器包装层故障（非业务异常）
     */
    private boolean executeBatch(
            List<SemanticTask> tasks,
            com.portfolio.agent.turn.planning.SemanticTurnPlan plan,
            Map<String, TaskOutcome> outcomes,
            TurnDeadline deadline,
            CancellationSignal cancellation,
            String expressionTaskId,
            boolean presetRequest,
            ResolvedModelExecution modelExecution) {
        CompletionService<TaskOutcome> completions = new ExecutorCompletionService<>(executorService);
        Map<Future<TaskOutcome>, String> taskIds = new HashMap<>();
        LateResultGate gate = new LateResultGate();
        for (SemanticTask task : tasks) {
            Future<TaskOutcome> future = completions.submit(() -> executeTask(task,
                    scheduler.dependencyResults(plan, task.getTaskId(), outcomes),
                    plan.getContentReleaseId(), deadline, cancellation,
                    task.getTaskId().equals(expressionTaskId), presetRequest,
                    modelExecution));
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
            if (failure.getCause() instanceof SelectedModelFailureException selected) {
                throw selected;
            }
            throw new IllegalStateException("executor wrapper failed", failure.getCause());
        } finally {
            gate.settle();
            for (Future<TaskOutcome> future : taskIds.keySet()) {
                if (!future.isDone()) future.cancel(true);
            }
        }
        return remaining == 0;
    }

    /**
     * 在工作线程内执行单个任务并把任何结果形态折叠为 {@link TaskOutcome}：
     * 正常返回映射为 Produced，{@link TaskTerminalException} 按 Kind 映射为
     * NoResult/Rejected/Failed，其余 RuntimeException 兜底为 FAILED(EXECUTION_FAILED)
     * （不泄露内部异常细节）。该 SourceDomain 没有注册 Executor 时直接
     * FAILED(CAPABILITY_UNAVAILABLE)，保持 fail-closed。
     */
    private TaskOutcome executeTask(
            SemanticTask task, List<TaskSemanticResult> dependencyResults,
            String contentReleaseId, TurnDeadline deadline, CancellationSignal cancellation,
            boolean modelExpressionAllowed, boolean presetRequest,
            ResolvedModelExecution modelExecution) {
        SemanticTaskExecutor executor = executors.get(task.getSourceDomain());
        if (executor == null) return new TaskOutcome(task.getTaskId(),
                new TaskOutcome.Failed(TaskTerminalReason.CAPABILITY_UNAVAILABLE));
        try {
            TaskExecutionResult result = executor.execute(new TaskExecutionContext(
                    task, dependencyResults, contentReleaseId, deadline, cancellation,
                    modelExpressionAllowed, presetRequest, modelExecution));
            return new TaskOutcome(task.getTaskId(),
                    new TaskOutcome.Produced(result.getArtifact(), result.getFulfillment()));
        } catch (TaskTerminalException terminal) {
            TaskOutcome.Terminal value = switch (terminal.getKind()) {
                case NO_RESULT -> new TaskOutcome.NoResult(terminal.getReason());
                case REJECTED -> new TaskOutcome.Rejected(terminal.getReason());
                case FAILED -> new TaskOutcome.Failed(terminal.getReason());
            };
            return new TaskOutcome(task.getTaskId(), value);
        } catch (SelectedModelFailureException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            return new TaskOutcome(task.getTaskId(),
                    new TaskOutcome.Failed(TaskTerminalReason.EXECUTION_FAILED));
        }
    }

    /** 按 SourceDomain 建立不可变索引；同域出现第二个 Executor 时抛出 IllegalArgumentException。 */
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
