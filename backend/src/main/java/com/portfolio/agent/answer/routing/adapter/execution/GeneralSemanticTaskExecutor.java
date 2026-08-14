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
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SemanticTaskType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskExecutionContext;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.TaskOutcome;
import com.portfolio.agent.answer.routing.domain.TaskResultPayload;
import com.portfolio.agent.answer.routing.domain.TaskResultProvenance;
import com.portfolio.agent.answer.routing.service.SemanticTaskExecutor;
import com.portfolio.agent.answer.general.service.GeneralMaterialPipeline;
import com.portfolio.agent.answer.runtime.ModelOperation;
import com.portfolio.agent.answer.runtime.ModelOperationPolicyRegistry;

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
    public TaskSourceDomain getSourceDomain() {
        return TaskSourceDomain.GENERAL;
    }

    @Override
    public TaskOutcome execute(SemanticTaskExecutionContext context) {
        Objects.requireNonNull(context, "context");
        return execute(
                context.getSemanticTask(),
                context.getDependencyOutcomes(),
                context.getExpectedContentVersion());
    }

    /** Compatibility adapter retained until the P3-E production cutover. */
    public TaskOutcome execute(SemanticTask task, List<TaskOutcome> availableDependencyOutcomes) {
        return execute(task, availableDependencyOutcomes, "compatibility-general-v1");
    }

    private TaskOutcome execute(
            SemanticTask task,
            List<TaskOutcome> availableDependencyOutcomes,
            String expectedContentVersion) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(availableDependencyOutcomes, "availableDependencyOutcomes");
        if (task.getSourceDomain() != TaskSourceDomain.GENERAL || !isSupported(task.getTaskType())) {
            return TaskOutcome.notSupported(task.getTaskId(), TaskSourceDomain.GENERAL,
                    false, "GENERAL_TASK_UNSUPPORTED");
        }
        if (!providerAccess.isAllowed()) {
            return TaskOutcome.capabilityUnavailable(
                    task.getTaskId(), TaskSourceDomain.GENERAL, "GENERAL_PROVIDER_UNAVAILABLE");
        }
        GeneralMaterialPipeline.Result generated = materialPipeline.generate(
                question(task),
                new ConversationWindow(null, List.of(), 0),
                generalRoute(),
                expectedContentVersion,
                audienceRole(task));
        if (!generated.isSuccessful() && "GENERAL_PROVIDER_UNAVAILABLE".equals(generated.getFailureCode())) {
            return TaskOutcome.capabilityUnavailable(task.getTaskId(), TaskSourceDomain.GENERAL,
                    "GENERAL_PROVIDER_UNAVAILABLE");
        }
        if (operationPolicies.get(ModelOperation.GENERAL_ANSWER_MATERIAL).getMode()
                != com.portfolio.agent.answer.runtime.OperationMode.ENABLED) {
            return TaskOutcome.capabilityUnavailable(task.getTaskId(), TaskSourceDomain.GENERAL,
                    "GENERAL_ANSWER_MATERIAL_DISABLED");
        }
        if (!generated.isSuccessful()) {
            return TaskOutcome.notSupported(task.getTaskId(), TaskSourceDomain.GENERAL,
                    false, "GENERAL_DRAFT_REJECTED");
        }
        return TaskOutcome.answered(
                task.getTaskId(),
                TaskSourceDomain.GENERAL,
                generated.getPayload(),
                TaskResultProvenance.direct(TaskSourceDomain.GENERAL, List.of(), List.of()),
                false);
    }

    private String question(SemanticTask task) {
        if (task.getParameters() instanceof SemanticTaskParameters.GeneralExplanation explanation) {
            return explanation.getTopic()
                    + "\nDepth: " + explanation.getDepth()
                    + "\nAudience: " + explanation.getAudienceRole();
        }
        if (task.getParameters() instanceof SemanticTaskParameters.GeneralComparison comparison) {
            return String.join(" vs ", comparison.getSubjects())
                    + "\nDimensions: " + comparison.getDimensions()
                    + "\nDepth: " + comparison.getDepth()
                    + "\nAudience: " + comparison.getAudienceRole();
        }
        throw new IllegalArgumentException("unsupported general task parameters");
    }

    private String audienceRole(SemanticTask task) {
        if (task.getParameters() instanceof SemanticTaskParameters.GeneralExplanation explanation) {
            return explanation.getAudienceRole().name();
        }
        if (task.getParameters() instanceof SemanticTaskParameters.GeneralComparison comparison) {
            return comparison.getAudienceRole().name();
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

    private boolean isSupported(SemanticTaskType taskType) {
        return taskType == SemanticTaskType.GENERAL_EXPLANATION
                || taskType == SemanticTaskType.GENERAL_COMPARISON;
    }
}
