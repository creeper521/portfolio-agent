package com.portfolio.agent.answer.routing.adapter.execution;

import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationDraft;
import com.portfolio.agent.answer.domain.ConversationDraftValidationResult;
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
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.TaskOutcome;
import com.portfolio.agent.answer.routing.domain.TaskResultPayload;
import com.portfolio.agent.answer.routing.domain.TaskResultProvenance;
import com.portfolio.agent.answer.routing.service.SemanticTaskExecutor;
import com.portfolio.agent.answer.service.ConversationDraftValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Executes an already classified general task through the existing generation and draft-validation path. */
public final class GeneralSemanticTaskExecutor implements SemanticTaskExecutor {

    private final ConversationProviderAccess providerAccess;
    private final ConversationalModelPort modelPort;
    private final ConversationDraftValidator draftValidator;

    public GeneralSemanticTaskExecutor(
            ConversationProviderAccess providerAccess,
            ConversationalModelPort modelPort,
            ConversationDraftValidator draftValidator) {
        this.providerAccess = Objects.requireNonNull(providerAccess, "providerAccess");
        this.modelPort = Objects.requireNonNull(modelPort, "modelPort");
        this.draftValidator = Objects.requireNonNull(draftValidator, "draftValidator");
    }

    @Override
    public TaskSourceDomain getSourceDomain() {
        return TaskSourceDomain.GENERAL;
    }

    @Override
    public TaskOutcome execute(SemanticTask task, List<TaskOutcome> availableDependencyOutcomes) {
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
        ConversationModelResult<ConversationDraft> generated = modelPort.generate(
                question(task), new ConversationWindow(null, List.of(), 0), generalRoute(),
                PortfolioGroundingContext.empty());
        if (generated == null || !generated.isSuccessful()) {
            return TaskOutcome.capabilityUnavailable(
                    task.getTaskId(), TaskSourceDomain.GENERAL, "GENERAL_PROVIDER_UNAVAILABLE");
        }
        ConversationDraftValidationResult validation = draftValidator.validate(
                generated.getValue(), ConversationAnswerScope.GENERAL, PortfolioGroundingContext.empty());
        if (validation == null || !validation.isValid() || validation.getAcceptedBlocks().isEmpty()) {
            return TaskOutcome.notSupported(task.getTaskId(), TaskSourceDomain.GENERAL,
                    false, "GENERAL_DRAFT_REJECTED");
        }
        List<String> blocks = new ArrayList<>();
        for (ConversationAnswerBlock block : validation.getAcceptedBlocks()) {
            blocks.add(block.getContent());
        }
        if (blocks.isEmpty()) {
            return TaskOutcome.notSupported(task.getTaskId(), TaskSourceDomain.GENERAL,
                    false, "GENERAL_DRAFT_REJECTED");
        }
        return TaskOutcome.answered(
                task.getTaskId(),
                TaskSourceDomain.GENERAL,
                new TaskResultPayload.SectionResultPayload(blocks, validation.getTitle()),
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
