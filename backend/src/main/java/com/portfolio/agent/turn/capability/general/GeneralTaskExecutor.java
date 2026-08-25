package com.portfolio.agent.turn.capability.general;

import com.portfolio.agent.turn.execution.SemanticTaskExecutor;
import com.portfolio.agent.turn.execution.TaskArtifact;
import com.portfolio.agent.turn.execution.TaskExecutionContext;
import com.portfolio.agent.turn.execution.TaskExecutionResult;
import com.portfolio.agent.turn.execution.TaskProvenance;
import com.portfolio.agent.turn.execution.TaskTerminalException;
import com.portfolio.agent.turn.execution.TaskTerminalReason;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.UserGoalProposal;

import java.util.Objects;

/**
 * 通用对话能力的任务执行器（GENERAL 域的 {@link SemanticTaskExecutor} 实现）：
 * 把计划任务参数翻译为 {@link GeneralKnowledgeRequest}，经
 * {@link GeneralKnowledgeGenerator} 生成语义结果，再组装展示与空溯源
 * （通用知识不引用公开 Evidence）。
 *
 * <p>终态收敛：进入时已取消 → FAILED(TURN_CANCELLED)；任务参数不是认可的
 * 通用解释/对比参数形状 → REJECTED(INPUT_REJECTED)；生成不可用 →
 * FAILED(CAPABILITY_UNAVAILABLE)。成功路径固定返回 FULL 满足度。
 */
public final class GeneralTaskExecutor implements SemanticTaskExecutor {
    private final GeneralKnowledgeGenerator generator;
    private final GeneralPresentationComposer presentationComposer;

    public GeneralTaskExecutor(
            GeneralKnowledgeGenerator generator,
            GeneralPresentationComposer presentationComposer) {
        this.generator = Objects.requireNonNull(generator, "generator");
        this.presentationComposer = Objects.requireNonNull(presentationComposer, "presentationComposer");
    }

    @Override public SemanticTask.SourceDomain getSourceDomain() { return SemanticTask.SourceDomain.GENERAL; }

    /**
     * 执行一个 GENERAL 域任务：先检查取消信号，再构造请求并生成结果，
     * 产出语义结果 + 展示 + 空溯源，满足度固定 FULL。
     *
     * @throws TaskTerminalException 已取消（FAILED/TURN_CANCELLED）、参数形状
     *         不识别（REJECTED/INPUT_REJECTED）或生成不可用
     *         （FAILED/CAPABILITY_UNAVAILABLE）
     */
    @Override public TaskExecutionResult execute(TaskExecutionContext context) {
        Objects.requireNonNull(context, "context");
        if (context.getCancellation().isCancelled()) {
            throw new TaskTerminalException(TaskTerminalException.Kind.FAILED, TaskTerminalReason.TURN_CANCELLED);
        }
        GeneralKnowledgeRequest request = request(context);
        try {
            GeneralSemanticResult result = generator.generate(
                    request, context.getModelExecution());
            return TaskExecutionResult.full(new TaskArtifact(
                    result, presentationComposer.compose(result), TaskProvenance.none()));
        } catch (GeneralKnowledgeUnavailableException exception) {
            throw new TaskTerminalException(
                    TaskTerminalException.Kind.FAILED, TaskTerminalReason.CAPABILITY_UNAVAILABLE);
        }
    }

    /**
     * 由任务参数构造请求：只接受 GeneralExplanationParameters 与
     * GeneralComparisonParameters 两种形状，受众画像取自任务参数；
     * 其余任何形状一律拒绝。
     */
    private GeneralKnowledgeRequest request(TaskExecutionContext context) {
        SemanticTask task = context.getTask();
        if (task.getSourceDomain() != SemanticTask.SourceDomain.GENERAL) {
            throw new TaskTerminalException(TaskTerminalException.Kind.REJECTED, TaskTerminalReason.INPUT_REJECTED);
        }
        if (task.getParameters().getParameters()
                instanceof UserGoalProposal.GeneralExplanationParameters value) {
            return GeneralKnowledgeRequest.explanation(
                    value.getTopicAnchor().getText(), value.getDepth(),
                    audience(task),
                    context.getContentReleaseId(), context.getDeadline());
        }
        if (task.getParameters().getParameters()
                instanceof UserGoalProposal.GeneralComparisonParameters value) {
            return GeneralKnowledgeRequest.comparison(
                    value.getSubjectAnchors().stream().map(UserGoalProposal.InputAnchor::getText).toList(),
                    value.getDimensions(), audience(task),
                    context.getContentReleaseId(), context.getDeadline());
        }
        throw new TaskTerminalException(TaskTerminalException.Kind.REJECTED, TaskTerminalReason.INPUT_REJECTED);
    }

    /** 任务受众画像枚举名到请求 Audience 的同名映射。 */
    private GeneralKnowledgeRequest.Audience audience(SemanticTask task) {
        return GeneralKnowledgeRequest.Audience.valueOf(
                task.getParameters().getAudienceProfile().name());
    }
}
