package com.portfolio.agent.answer.routing.adapter.execution;

import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationDraft;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.domain.ConversationProviderAccess;
import com.portfolio.agent.answer.domain.ConversationRoute;
import com.portfolio.agent.answer.domain.ConversationWindow;
import com.portfolio.agent.answer.domain.PortfolioGroundingContext;
import com.portfolio.agent.answer.domain.PortfolioKnowledgeFacet;
import com.portfolio.agent.answer.gateway.ConversationalModelPort;
import com.portfolio.agent.turn.execution.SemanticTaskExecutor;
import com.portfolio.agent.turn.execution.TaskArtifact;
import com.portfolio.agent.turn.execution.TaskExecutionContext;
import com.portfolio.agent.turn.execution.TaskExecutionResult;
import com.portfolio.agent.turn.execution.TaskProvenance;
import com.portfolio.agent.turn.execution.TaskTerminalException;
import com.portfolio.agent.turn.execution.TaskTerminalReason;
import com.portfolio.agent.answer.general.service.GeneralMaterialPipeline;
import com.portfolio.agent.answer.runtime.ModelOperation;
import com.portfolio.agent.answer.runtime.ModelOperationPolicyRegistry;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.UserGoalProposal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Executes an already classified general task through the existing generation and draft-validation path. */
public final class GeneralSemanticTaskExecutor implements SemanticTaskExecutor {

    private final ConversationProviderAccess providerAccess;
    private final ConversationalModelPort modelPort;
    private final GeneralMaterialPipeline materialPipeline;
    private final ModelOperationPolicyRegistry operationPolicies;

    public GeneralSemanticTaskExecutor(
            ConversationProviderAccess providerAccess,
            ConversationalModelPort modelPort,
            com.portfolio.agent.answer.service.ConversationDraftValidator draftValidator,
            ModelOperationPolicyRegistry operationPolicies) {
        this.providerAccess = Objects.requireNonNull(providerAccess, "providerAccess");
        this.modelPort = Objects.requireNonNull(modelPort, "modelPort");
        Objects.requireNonNull(draftValidator, "draftValidator");
        this.operationPolicies = Objects.requireNonNull(operationPolicies, "operationPolicies");
        this.materialPipeline = new GeneralMaterialPipeline(providerAccess, modelPort, operationPolicies);
    }

    @Override
    public SemanticTask.SourceDomain getSourceDomain() {
        return SemanticTask.SourceDomain.GENERAL;
    }

    @Override
    public TaskExecutionResult execute(TaskExecutionContext context) {
        Objects.requireNonNull(context, "context");
        return execute(
                context.getTask(), context.getContentReleaseId());
    }

    private TaskExecutionResult execute(SemanticTask task, String expectedContentVersion) {
        Objects.requireNonNull(task, "task");
        if (task.getSourceDomain() != SemanticTask.SourceDomain.GENERAL || !isSupported(task.getType())) {
            throw new TaskTerminalException(
                    TaskTerminalException.Kind.REJECTED, TaskTerminalReason.INPUT_REJECTED);
        }
        if (!providerAccess.isAllowed()) {
            throw new TaskTerminalException(
                    TaskTerminalException.Kind.FAILED, TaskTerminalReason.CAPABILITY_UNAVAILABLE);
        }
        GeneralMaterialPipeline.Result generated = materialPipeline.generate(
                question(task),
                new ConversationWindow(null, List.of(), 0),
                generalRoute(),
                expectedContentVersion,
                audienceRole(task));
        if (!generated.isSuccessful() && "GENERAL_PROVIDER_UNAVAILABLE".equals(generated.getFailureCode())) {
            throw new TaskTerminalException(
                    TaskTerminalException.Kind.FAILED, TaskTerminalReason.CAPABILITY_UNAVAILABLE);
        }
        if (operationPolicies.get(ModelOperation.GENERAL_ANSWER_MATERIAL).getMode()
                != com.portfolio.agent.answer.runtime.OperationMode.ENABLED) {
            throw new TaskTerminalException(
                    TaskTerminalException.Kind.FAILED, TaskTerminalReason.CAPABILITY_UNAVAILABLE);
        }
        if (!generated.isSuccessful()) {
            throw new TaskTerminalException(
                    TaskTerminalException.Kind.NO_RESULT, TaskTerminalReason.NO_SUPPORTED_RESULT);
        }
        return TaskExecutionResult.full(new TaskArtifact(
                generated.getMaterial(), generated.getPresentation(), TaskProvenance.none()));
    }

    private String question(SemanticTask task) {
        if (task.getParameters().getParameters()
                instanceof UserGoalProposal.GeneralExplanationParameters explanation) {
            return explanation.getTopicAnchor().getText()
                    + "\nDepth: " + explanation.getDepth()
                    + "\nAudience: GUEST";
        }
        if (task.getParameters().getParameters()
                instanceof UserGoalProposal.GeneralComparisonParameters comparison) {
            return String.join(" vs ", comparison.getSubjectAnchors().stream()
                            .map(UserGoalProposal.InputAnchor::getText).toList())
                    + "\nDimensions: " + comparison.getDimensions()
                    + "\nDepth: STANDARD"
                    + "\nAudience: GUEST";
        }
        throw new IllegalArgumentException("unsupported general task parameters");
    }

    private String audienceRole(SemanticTask task) {
        if (task.getParameters().getParameters()
                instanceof UserGoalProposal.GeneralExplanationParameters
                || task.getParameters().getParameters()
                instanceof UserGoalProposal.GeneralComparisonParameters) {
            return "GUEST";
        }
        throw new IllegalArgumentException("unsupported general task parameters");
    }

    private ConversationRoute generalRoute() {
        return new ConversationRoute(
                ConversationIntent.GENERAL_KNOWLEDGE,
                ConversationAnswerScope.GENERAL,
                1.0d,
                null,
                null,
                PortfolioKnowledgeFacet.OVERVIEW,
                false);
    }

    private boolean isSupported(SemanticTask.Type taskType) {
        return taskType == SemanticTask.Type.GENERAL_EXPLANATION
                || taskType == SemanticTask.Type.GENERAL_COMPARISON;
    }
}
