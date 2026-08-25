package com.portfolio.agent.turn.capability.portfolio;

import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceBundle;
import com.portfolio.agent.turn.capability.portfolio.presentation.PortfolioPresentation;
import com.portfolio.agent.turn.capability.portfolio.presentation.PortfolioPresentationComposer;
import com.portfolio.agent.turn.capability.portfolio.semantic.PortfolioSemanticResult;
import com.portfolio.agent.turn.capability.portfolio.semantic.PortfolioSemanticResultFactory;
import com.portfolio.agent.turn.execution.SemanticTaskExecutor;
import com.portfolio.agent.turn.execution.TaskArtifact;
import com.portfolio.agent.turn.execution.TaskExecutionContext;
import com.portfolio.agent.turn.execution.TaskExecutionResult;
import com.portfolio.agent.turn.execution.TaskProvenance;
import com.portfolio.agent.turn.execution.TaskTerminalException;
import com.portfolio.agent.turn.execution.TaskTerminalReason;
import com.portfolio.agent.turn.planning.SemanticTask;

import java.util.List;
import java.util.Objects;

/**
 * PORTFOLIO 域 SemanticTask 的直线式执行器。
 *
 * <p>管线：Invocation 装配 → Evidence 能力检索与晋级 → 语义结果构建 →
 * 呈现组装 → 打包为 TaskArtifact；语义结果 Coverage 为 FULL 时产出完整结果，
 * 否则产出部分结果。能力失败映射为 CAPABILITY_UNAVAILABLE 终止，
 * 参数非法映射为 INPUT_REJECTED 终止。
 */
public final class PortfolioTaskExecutor implements SemanticTaskExecutor {
    private final PortfolioInvocationFactory invocationFactory;
    private final PortfolioEvidenceCapability capability;
    private final PortfolioSemanticResultFactory resultFactory;
    private final PortfolioPresentationComposer presentationComposer;
    public PortfolioTaskExecutor(
            PortfolioInvocationFactory invocationFactory,
            PortfolioEvidenceCapability capability,
            PortfolioSemanticResultFactory resultFactory,
            PortfolioPresentationComposer presentationComposer) {
        this.invocationFactory = Objects.requireNonNull(invocationFactory, "invocationFactory");
        this.capability = Objects.requireNonNull(capability, "capability");
        this.resultFactory = Objects.requireNonNull(resultFactory, "resultFactory");
        this.presentationComposer = Objects.requireNonNull(
                presentationComposer, "presentationComposer");
    }

    @Override public SemanticTask.SourceDomain getSourceDomain() {
        return SemanticTask.SourceDomain.PORTFOLIO;
    }

    /**
     * 执行一次作品集任务并产出执行结果。
     *
     * <p>组装 Artifact 时从语义结果提取去重后的来源引用键作为 Provenance。
     * 结果工厂产出为空时以 NO_RESULT 终止；{@link PortfolioEvidenceCapability}
     * 抛出的能力异常统一转换为 FAILED/CAPABILITY_UNAVAILABLE 的终止异常。
     *
     * @param context 当前任务执行上下文
     * @return 完整或部分执行结果（按语义结果的 Coverage 判定）
     * @throws TaskTerminalException 能力不可用、无受支持结果或输入被拒绝时抛出
     */
    @Override public TaskExecutionResult execute(TaskExecutionContext context) {
        try {
            PortfolioEvidenceInvocation invocation = invocationFactory.create(context);
            ValidatedEvidenceBundle evidence = capability.execute(invocation, context.getDeadline());
            PortfolioSemanticResult result = resultFactory.create(
                    context.getTask(), invocation, evidence).orElseThrow(() ->
                    new TaskTerminalException(
                            TaskTerminalException.Kind.NO_RESULT,
                            TaskTerminalReason.NO_SUPPORTED_RESULT));
            PortfolioPresentation presentation = presentationComposer.compose(result);
            List<String> sourceKeys = result.getUnits().stream()
                    .map(unit -> unit.getSourceReference().getReferenceKey()).distinct().toList();
            TaskArtifact artifact = new TaskArtifact(
                    result, presentation, new TaskProvenance(sourceKeys));
            return result.getCoverage() == PortfolioSemanticResult.Coverage.FULL
                    ? TaskExecutionResult.full(artifact) : TaskExecutionResult.partial(artifact);
        } catch (PortfolioEvidenceCapability.PortfolioCapabilityException failure) {
            throw new TaskTerminalException(
                    TaskTerminalException.Kind.FAILED, TaskTerminalReason.CAPABILITY_UNAVAILABLE);
        } catch (TaskTerminalException failure) {
            throw failure;
        } catch (IllegalArgumentException failure) {
            throw new TaskTerminalException(
                    TaskTerminalException.Kind.REJECTED, TaskTerminalReason.INPUT_REJECTED);
        }
    }
}
