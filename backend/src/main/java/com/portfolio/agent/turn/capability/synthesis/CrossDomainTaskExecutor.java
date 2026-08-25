package com.portfolio.agent.turn.capability.synthesis;

import com.portfolio.agent.turn.capability.general.GeneralSemanticResult;
import com.portfolio.agent.turn.capability.portfolio.semantic.PortfolioSemanticResult;
import com.portfolio.agent.turn.execution.SemanticTaskExecutor;
import com.portfolio.agent.turn.execution.TaskArtifact;
import com.portfolio.agent.turn.execution.TaskExecutionContext;
import com.portfolio.agent.turn.execution.TaskExecutionResult;
import com.portfolio.agent.turn.execution.TaskProvenance;
import com.portfolio.agent.turn.execution.TaskSemanticResult;
import com.portfolio.agent.turn.execution.TaskTerminalException;
import com.portfolio.agent.turn.execution.TaskTerminalReason;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.UserGoalProposal;

import java.util.List;
import java.util.Objects;

/**
 * Exact one-General plus one-Portfolio fan-in anchored by the planned concept.
 *
 * <p>跨域综合任务执行器（SYNTHESIS 域）：严格按照"恰一个通用语义结果 + 恰一个
 * Portfolio 语义结果"的扇入结构消费依赖数据，以计划锚定的概念为轴做综合——
 * 概念与通用主题归一化后必须一致，通用侧只保留 DEFINITION/MECHANISM 陈述，
 * Portfolio 侧逐条升格为带公开来源的落地陈述，最后产出语义结果、展示与
 * 去重后的公开溯源键。任何结构或锚定不符都以受控终态拒绝，不猜测综合。
 */
public final class CrossDomainTaskExecutor implements SemanticTaskExecutor {
    private final CrossDomainPresentationComposer presentationComposer;

    public CrossDomainTaskExecutor(CrossDomainPresentationComposer presentationComposer) {
        this.presentationComposer = Objects.requireNonNull(presentationComposer, "presentationComposer");
    }

    @Override public SemanticTask.SourceDomain getSourceDomain() { return SemanticTask.SourceDomain.SYNTHESIS; }

    /**
     * 执行跨域综合：校验任务形状与扇入结构 → 校验概念锚与通用主题一致 →
     * 筛选两侧陈述并组装综合结果。成功固定 FULL 满足度；溯源键来自项目陈述
     * 的来源引用并去重。
     *
     * @throws TaskTerminalException 任务域或参数形状不符（REJECTED/INPUT_REJECTED）；
     *         依赖不是"恰一个 General + 恰一个 Portfolio"（NO_RESULT/DEPENDENCY_UNAVAILABLE）；
     *         概念锚不匹配或两侧筛选后为空（NO_RESULT/NO_SUPPORTED_RESULT）
     */
    @Override public TaskExecutionResult execute(TaskExecutionContext context) {
        Objects.requireNonNull(context, "context");
        if (context.getTask().getSourceDomain() != SemanticTask.SourceDomain.SYNTHESIS
                || !(context.getTask().getParameters().getParameters()
                instanceof UserGoalProposal.ApplyConceptParameters parameters)) {
            throw new TaskTerminalException(TaskTerminalException.Kind.REJECTED, TaskTerminalReason.INPUT_REJECTED);
        }
        GeneralSemanticResult general = exactlyOne(context.getDependencyResults(), GeneralSemanticResult.class);
        PortfolioSemanticResult portfolio = exactlyOne(context.getDependencyResults(), PortfolioSemanticResult.class);
        if (general == null || portfolio == null || context.getDependencyResults().size() != 2) {
            throw new TaskTerminalException(TaskTerminalException.Kind.NO_RESULT, TaskTerminalReason.DEPENDENCY_UNAVAILABLE);
        }
        String concept = parameters.getConceptAnchor().getText();
        if (!normalize(concept).equals(normalize(general.getTopic()))) {
            throw new TaskTerminalException(TaskTerminalException.Kind.NO_RESULT, TaskTerminalReason.NO_SUPPORTED_RESULT);
        }
        List<GeneralSemanticResult.Statement> selectedGeneral = general.getStatements().stream()
                .filter(value -> value.getRole() == GeneralSemanticResult.Role.DEFINITION
                        || value.getRole() == GeneralSemanticResult.Role.MECHANISM)
                .toList();
        List<CrossDomainSemanticResult.GroundedPortfolioStatement> selectedPortfolio =
                portfolio.getUnits().stream().map(value ->
                        new CrossDomainSemanticResult.GroundedPortfolioStatement(
                                value.getSubjectId(), value.getClaim().getCategory(),
                                value.getClaim().getStatement(),
                                value.getSourceReference())).toList();
        if (selectedGeneral.isEmpty() || selectedPortfolio.isEmpty()) {
            throw new TaskTerminalException(TaskTerminalException.Kind.NO_RESULT, TaskTerminalReason.NO_SUPPORTED_RESULT);
        }
        CrossDomainSemanticResult result = new CrossDomainSemanticResult(
                concept, selectedGeneral, selectedPortfolio, general.getCaveats());
        List<String> sourceKeys = selectedPortfolio.stream()
                .map(value -> value.sourceReference().getReferenceKey()).distinct().toList();
        return TaskExecutionResult.full(new TaskArtifact(
                result, presentationComposer.compose(result), new TaskProvenance(sourceKeys)));
    }

    /** 在依赖结果中查找指定类型的唯一实例：恰有一个返回它，零个或多个返回 null。 */
    private <T extends TaskSemanticResult> T exactlyOne(
            List<TaskSemanticResult> values, Class<T> type) {
        List<T> matches = values.stream().filter(type::isInstance).map(type::cast).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    /** 概念文本归一化：去首尾空白、转小写、连续空白折叠为单空格，用于锚定比对。 */
    private String normalize(String value) {
        return value.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    }
}
