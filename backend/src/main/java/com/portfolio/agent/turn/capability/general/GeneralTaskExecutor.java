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

    private GeneralKnowledgeRequest.Audience audience(SemanticTask task) {
        return GeneralKnowledgeRequest.Audience.valueOf(
                task.getParameters().getAudienceProfile().name());
    }
}
