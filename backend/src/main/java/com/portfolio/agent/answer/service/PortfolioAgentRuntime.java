package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerDecision;
import com.portfolio.agent.answer.domain.AgentExecutionSnapshot;
import com.portfolio.agent.answer.domain.AnswerPlan;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerResult;
import com.portfolio.agent.answer.domain.AnswerSection;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.AnswerTurnSnapshot;
import com.portfolio.agent.answer.domain.DurationBucket;
import com.portfolio.agent.answer.domain.GeneratedAnswer;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.domain.QuestionKind;
import com.portfolio.agent.answer.domain.QuestionResolution;
import com.portfolio.agent.answer.domain.RetrievalCapability;
import com.portfolio.agent.answer.domain.RetrievalDecision;
import com.portfolio.agent.answer.domain.RetrievalDecisionType;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import com.portfolio.agent.answer.domain.ResolvedAnswerContext;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.domain.VerificationStatus;
import com.portfolio.agent.answer.domain.ContextResolution;
import com.portfolio.agent.answer.domain.ContextResolutionType;
import com.portfolio.agent.answer.domain.ExecutionBudgets;
import com.portfolio.agent.answer.domain.PublicToolResultStatus;
import com.portfolio.agent.answer.domain.ToolExecutionOutcome;
import com.portfolio.agent.answer.domain.ToolPlan;
import com.portfolio.agent.answer.domain.ValidatedContextEnvelope;
import com.portfolio.agent.answer.dto.request.AnswerRequest;
import com.portfolio.agent.answer.gateway.AnswerDecisionPublisher;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public final class PortfolioAgentRuntime {

    private static final String BOUNDARY_MESSAGE =
            "当前版本只支持下方已发布问题。请选择一个受支持问题继续。";
    private static final String REJECTED_MESSAGE =
            "无法处理该请求。你可以改为询问已经公开的项目、职责、方案或验证信息。";

    private final PortfolioKnowledgeGateway knowledgeGateway;
    private final QuestionResolver questionResolver;
    private final AnswerContextFactory contextFactory;
    private final AnswerPlanBuilder answerPlanBuilder;
    private final AgentExecutionSnapshotFactory executionSnapshotFactory;
    private final ModelAnswerCoordinator modelAnswerCoordinator;
    private final VerificationPolicy verificationPolicy;
    private final AnswerDecisionPublisher decisionPublisher;
    private final LocalRetrievalCoordinator retrievalCoordinator;
    private final RetrievalPolicy retrievalPolicy;
    private final RetrievalCapability retrievalCapability;
    private final ContextEnvelopeValidator contextEnvelopeValidator;
    private final ToolPlanBuilder toolPlanBuilder;
    private final ToolPlanExecutor toolPlanExecutor;

    public PortfolioAgentRuntime(
            PortfolioKnowledgeGateway knowledgeGateway,
            QuestionResolver questionResolver,
            AnswerContextFactory contextFactory,
            AnswerPlanBuilder answerPlanBuilder,
            AgentExecutionSnapshotFactory executionSnapshotFactory,
            ModelAnswerCoordinator modelAnswerCoordinator,
            VerificationPolicy verificationPolicy,
            AnswerDecisionPublisher decisionPublisher,
            LocalRetrievalCoordinator retrievalCoordinator,
            RetrievalPolicy retrievalPolicy,
            RetrievalCapability retrievalCapability
    ) {
        this(knowledgeGateway, questionResolver, contextFactory, answerPlanBuilder,
                executionSnapshotFactory, modelAnswerCoordinator, verificationPolicy,
                decisionPublisher, retrievalCoordinator, retrievalPolicy, retrievalCapability,
                null, null, null);
    }

    @Autowired
    public PortfolioAgentRuntime(
            PortfolioKnowledgeGateway knowledgeGateway,
            QuestionResolver questionResolver,
            AnswerContextFactory contextFactory,
            AnswerPlanBuilder answerPlanBuilder,
            AgentExecutionSnapshotFactory executionSnapshotFactory,
            ModelAnswerCoordinator modelAnswerCoordinator,
            VerificationPolicy verificationPolicy,
            AnswerDecisionPublisher decisionPublisher,
            LocalRetrievalCoordinator retrievalCoordinator,
            RetrievalPolicy retrievalPolicy,
            RetrievalCapability retrievalCapability,
            ContextEnvelopeValidator contextEnvelopeValidator,
            ToolPlanBuilder toolPlanBuilder,
            ToolPlanExecutor toolPlanExecutor
    ) {
        this.knowledgeGateway = knowledgeGateway;
        this.questionResolver = questionResolver;
        this.contextFactory = contextFactory;
        this.answerPlanBuilder = answerPlanBuilder;
        this.executionSnapshotFactory = executionSnapshotFactory;
        this.modelAnswerCoordinator = modelAnswerCoordinator;
        this.verificationPolicy = verificationPolicy;
        this.decisionPublisher = decisionPublisher;
        this.retrievalCoordinator = retrievalCoordinator;
        this.retrievalPolicy = retrievalPolicy;
        this.retrievalCapability = retrievalCapability;
        this.contextEnvelopeValidator = contextEnvelopeValidator;
        this.toolPlanBuilder = toolPlanBuilder;
        this.toolPlanExecutor = toolPlanExecutor;
    }

    public AnswerResult answer(AnswerRequest request) {
        long startedAt = System.nanoTime();
        String requestId = UUID.randomUUID().toString();
        RuntimeAnswerContent content = knowledgeGateway.getContent();
        QuestionResolution resolution = questionResolver.resolve(content, request);
        ResolvedAnswerContext context;
        boolean contextVersionUpdated = false;
        if (shouldUseTools(request, resolution)) {
            ToolContextResolution toolContext = resolveToolContext(
                    content, request, requestId);
            context = toolContext == null ? null : toolContext.getContext();
            if (toolContext != null) {
                contextVersionUpdated = toolContext.isVersionUpdated();
                resolution = new QuestionResolution(
                        AnswerResolution.ANSWERED, context.getProject(), null);
            } else {
                AnswerTurnSnapshot boundaryTurn = createTurn(
                        requestId, content, resolution, request);
                context = contextFactory.create(boundaryTurn, resolution, request);
            }
        } else if (shouldRetrieve(resolution, content)) {
            RetrievalDecision retrievalDecision = retrieve(content, resolution, request);
            if (retrievalDecision != null
                    && retrievalDecision.getType() == RetrievalDecisionType.SUFFICIENT) {
                context = contextFactory.createRetrieval(
                        content, resolution.getProject(), retrievalDecision, request, requestId);
                resolution = new QuestionResolution(
                        AnswerResolution.ANSWERED, resolution.getProject(), null);
            } else {
                AnswerTurnSnapshot boundaryTurn = createTurn(
                        requestId, content, resolution, request);
                context = contextFactory.create(boundaryTurn, resolution, request);
            }
        } else {
            AnswerTurnSnapshot initialTurn = createTurn(requestId, content, resolution, request);
            context = contextFactory.create(initialTurn, resolution, request);
        }
        AnswerTurnSnapshot turn = context.getTurnSnapshot();
        AgentExecutionSnapshot execution = executionSnapshotFactory.create(turn);
        AnswerResult result = buildResult(
                turn, execution, resolution, context, contextVersionUpdated);
        publishBestEffort(result, request, startedAt);
        return result;
    }

    private boolean shouldUseTools(
            AnswerRequest request,
            QuestionResolution resolution
    ) {
        return request.getContextEnvelope() != null
                && resolution.getResolution() != AnswerResolution.REJECTED
                && contextEnvelopeValidator != null
                && toolPlanBuilder != null
                && toolPlanExecutor != null;
    }

    private ToolContextResolution resolveToolContext(
            RuntimeAnswerContent content,
            AnswerRequest request,
            String requestId
    ) {
        try {
            ContextResolution contextResolution = contextEnvelopeValidator.validate(
                    content, request.getContextEnvelope());
            if (contextResolution.getType() == ContextResolutionType.INVALID) {
                return null;
            }
            ValidatedContextEnvelope envelope = contextResolution.getEnvelope().orElseThrow();
            ExecutionBudgets budgets = new ExecutionBudgets(
                    5000L,
                    1,
                    4,
                    retrievalPolicy.getMaxClaims(),
                    retrievalPolicy.getMaxContextCharacters());
            ToolPlan toolPlan = toolPlanBuilder.build(
                    content, envelope.toQueryIntent(), budgets.getMaxToolCalls());
            ToolExecutionOutcome outcome = toolPlanExecutor.execute(
                    content, toolPlan, budgets);
            if (outcome.getStatus() != PublicToolResultStatus.SUCCESS) {
                return null;
            }
            ResolvedAnswerContext context = contextFactory.createTool(
                    content, envelope, outcome, request, requestId);
            return new ToolContextResolution(
                    context,
                    contextResolution.getType() == ContextResolutionType.VERSION_UPDATED);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean shouldRetrieve(
            QuestionResolution resolution,
            RuntimeAnswerContent content
    ) {
        return resolution.getResolution() == AnswerResolution.BOUNDARY
                && content.getRetrievalCorpus()
                        .map(retrievalCapability::supports)
                        .orElse(false);
    }

    private RetrievalDecision retrieve(
            RuntimeAnswerContent content,
            QuestionResolution resolution,
            AnswerRequest request
    ) {
        try {
            if (resolution.getProject().getSubjectType()
                    == com.portfolio.agent.answer.domain.AnswerSubjectType.PROJECT) {
                return retrievalCoordinator.retrieve(
                        request.getQuestion(),
                        resolution.getProject().getSlug(),
                        content.getRetrievalCorpus().orElseThrow(),
                        resolution.getProject().getClaims(),
                        resolution.getProject().getEvidence(),
                        retrievalCapability.getMode(),
                        retrievalPolicy);
            }
            return retrievalCoordinator.retrieve(
                    request.getQuestion(),
                    resolution.getProject().getSlug(),
                    resolution.getProject().getSubjectType(),
                    content.getRetrievalCorpus().orElseThrow(),
                    resolution.getProject().getClaims(),
                    resolution.getProject().getEvidence(),
                    retrievalCapability.getMode(),
                    retrievalPolicy);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private AnswerTurnSnapshot createTurn(
            String requestId,
            RuntimeAnswerContent content,
            QuestionResolution resolution,
            AnswerRequest request
    ) {
        return new AnswerTurnSnapshot(
                request.getTurnId(),
                requestId,
                content,
                resolution,
                request.getContext().getAudienceRole(),
                request.getContext().getSource());
    }

    private AnswerResult buildResult(
            AnswerTurnSnapshot turn,
            AgentExecutionSnapshot execution,
            QuestionResolution resolution,
            ResolvedAnswerContext context,
            boolean contextVersionUpdated
    ) {
        List<String> suggestions = resolution.getProject().getQuestions().stream()
                .map(com.portfolio.agent.answer.domain.AnswerQuestion::getId)
                .toList();
        if (resolution.getResolution() != AnswerResolution.ANSWERED) {
            String message = resolution.getResolution() == AnswerResolution.REJECTED
                    ? REJECTED_MESSAGE
                    : BOUNDARY_MESSAGE;
            AnswerSectionType type = resolution.getResolution() == AnswerResolution.REJECTED
                    ? AnswerSectionType.REJECTED
                    : AnswerSectionType.BOUNDARY;
            return new AnswerResult(
                    turn,
                    resolution.getResolution(),
                    null,
                    GenerationMode.DETERMINISTIC,
                    VerificationStatus.NOT_APPLICABLE,
                    resolution.getProject().getTitle(),
                    message,
                    List.of(new AnswerSection(type, "能力说明", message, List.of())),
                    List.of(),
                    suggestions
            );
        }

        AnswerPlan plan = answerPlanBuilder.build(turn, context);
        ModelAnswerOutcome outcome = modelAnswerCoordinator.generate(execution, plan);
        GeneratedAnswer generated = outcome.getAnswer();
        VerificationStatus verification = verificationPolicy.verify(
                resolution.getResolution(), context, generated);
        List<String> evidenceIds = context.getApprovedEvidence().stream()
                .map(com.portfolio.agent.answer.domain.AnswerEvidence::getId)
                .distinct()
                .toList();
        return new AnswerResult(
                turn,
                AnswerResolution.ANSWERED,
                context.getAnswerSource(),
                outcome.getGenerationMode(),
                verification,
                generated.getTitle(),
                generated.getSummary(),
                generated.getSections(),
                evidenceIds,
                suggestions,
                contextVersionUpdated
        );
    }

    private static final class ToolContextResolution {
        private final ResolvedAnswerContext context;
        private final boolean versionUpdated;

        private ToolContextResolution(
                ResolvedAnswerContext context,
                boolean versionUpdated
        ) {
            this.context = context;
            this.versionUpdated = versionUpdated;
        }

        private ResolvedAnswerContext getContext() { return context; }
        private boolean isVersionUpdated() { return versionUpdated; }
    }

    private void publishBestEffort(AnswerResult result, AnswerRequest request, long startedAt) {
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        DurationBucket bucket = durationBucket(elapsedMillis);
        QuestionKind kind = request.getQuestionPresetId() == null
                ? QuestionKind.FREE_TEXT
                : QuestionKind.PRESET;
        try {
            decisionPublisher.publish(new AnswerDecision(
                    Instant.now(), result.getTurnSnapshot(), kind, result, bucket, null));
        } catch (RuntimeException ignored) {
            // Observability is passive and must never change the visitor response.
        }
    }

    private DurationBucket durationBucket(long elapsedMillis) {
        if (elapsedMillis < 100) {
            return DurationBucket.LT_100_MS;
        }
        if (elapsedMillis < 500) {
            return DurationBucket.FROM_100_TO_499_MS;
        }
        if (elapsedMillis < 2000) {
            return DurationBucket.FROM_500_TO_1999_MS;
        }
        return DurationBucket.GE_2000_MS;
    }
}
