package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.ConversationProviderAccess;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.exception.PortfolioRetrievalFailedException;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.answer.intelligence.domain.AnswerIntentSource;
import com.portfolio.agent.answer.intelligence.domain.PortfolioClarification;
import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioContractTask;
import com.portfolio.agent.answer.intelligence.domain.PortfolioDecision;
import com.portfolio.agent.answer.intelligence.domain.PortfolioDisposition;
import com.portfolio.agent.answer.intelligence.domain.PortfolioFollowUpAction;
import com.portfolio.agent.answer.intelligence.domain.PortfolioIntelligenceResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendation;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedPassage;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedSubject;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalRequest;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTask;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTurn;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetriever;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetrievalException;
import com.portfolio.agent.selection.domain.EvidenceReference;
import com.portfolio.agent.selection.domain.PortfolioSubjectKind;
import com.portfolio.agent.selection.domain.SelectionCandidate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DefaultPortfolioIntelligence implements PortfolioIntelligence {

    private final PortfolioTaskValidator taskValidator;
    private final PortfolioRetriever retriever;
    private final PortfolioRecommendationPolicy recommendationPolicy;
    private final RecommendationContextValidator contextValidator;
    private final PortfolioKnowledgeGateway knowledgeGateway;
    private final PortfolioPresetResolver presetResolver;
    private final PortfolioReferenceContextValidator referenceContextValidator;
    private final PortfolioTaskResolver taskResolver;
    private final ConversationProviderAccess providerAccess;
    private final PortfolioRetrievalPlanner retrievalPlanner;
    private final ContractEvidenceSelector contractEvidenceSelector;

    public DefaultPortfolioIntelligence(
            PortfolioTaskValidator taskValidator,
            PortfolioRetriever retriever,
            PortfolioRecommendationPolicy recommendationPolicy,
            RecommendationContextValidator contextValidator) {
        this.taskValidator = Objects.requireNonNull(taskValidator, "taskValidator");
        this.retriever = Objects.requireNonNull(retriever, "retriever");
        this.recommendationPolicy = Objects.requireNonNull(recommendationPolicy, "recommendationPolicy");
        this.contextValidator = Objects.requireNonNull(contextValidator, "contextValidator");
        this.knowledgeGateway = null;
        this.presetResolver = null;
        this.referenceContextValidator = null;
        this.taskResolver = null;
        this.providerAccess = null;
        this.retrievalPlanner = new PortfolioRetrievalPlanner();
        this.contractEvidenceSelector = new ContractEvidenceSelector(retriever);
    }

    public DefaultPortfolioIntelligence(
            PortfolioTaskValidator taskValidator,
            PortfolioRetriever retriever,
            PortfolioRecommendationPolicy recommendationPolicy,
            RecommendationContextValidator contextValidator,
            PortfolioKnowledgeGateway knowledgeGateway,
            PortfolioPresetResolver presetResolver,
            PortfolioReferenceContextValidator referenceContextValidator,
            PortfolioTaskResolver taskResolver,
            ConversationProviderAccess providerAccess) {
        this.taskValidator = Objects.requireNonNull(taskValidator, "taskValidator");
        this.retriever = Objects.requireNonNull(retriever, "retriever");
        this.recommendationPolicy = Objects.requireNonNull(recommendationPolicy, "recommendationPolicy");
        this.contextValidator = Objects.requireNonNull(contextValidator, "contextValidator");
        this.knowledgeGateway = Objects.requireNonNull(knowledgeGateway, "knowledgeGateway");
        this.presetResolver = Objects.requireNonNull(presetResolver, "presetResolver");
        this.referenceContextValidator = Objects.requireNonNull(
                referenceContextValidator, "referenceContextValidator");
        this.taskResolver = Objects.requireNonNull(taskResolver, "taskResolver");
        this.providerAccess = Objects.requireNonNull(providerAccess, "providerAccess");
        this.retrievalPlanner = new PortfolioRetrievalPlanner();
        this.contractEvidenceSelector = new ContractEvidenceSelector(retriever);
    }

    @Override
    public PortfolioDecision tryResolve(PortfolioTurn turn) {
        try {
            return resolveTurn(turn);
        } catch (PortfolioRetrievalException exception) {
            throw new PortfolioRetrievalFailedException(exception);
        }
    }

    private PortfolioDecision resolveTurn(PortfolioTurn turn) {
        Objects.requireNonNull(turn, "turn");
        if (knowledgeGateway == null) {
            throw new IllegalStateException("turn resolution dependencies are not configured");
        }
        RuntimeAnswerContent content = knowledgeGateway.getContent();
        if (turn.getReferenceContext() != null) {
            return resolveReference(turn, content);
        }
        PortfolioPresetResolution preset = presetResolver.resolve(turn, content);
        if (preset.getType() == PortfolioPresetResolutionType.INVALID) {
            return contractUnavailable("PRESET_CONTRACT_UNAVAILABLE", null, null, content);
        }
        if (preset.getType() == PortfolioPresetResolutionType.STALE) {
            return contractUnavailable(
                    "PRESET_CONTRACT_STALE", preset.getQuestionPresetId(),
                    preset.getLatestContractVersion(), content);
        }
        if (preset.getType() == PortfolioPresetResolutionType.UNAVAILABLE) {
            return contractUnavailable(
                    "PRESET_CONTRACT_UNAVAILABLE", preset.getQuestionPresetId(), null, content);
        }
        if (preset.getType() == PortfolioPresetResolutionType.MATCHED) {
            if (preset.getContractTask() != null) {
                return executeContract(preset.getContractTask());
            }
            return execute(preset.getTask(), AnswerIntentSource.PRESET, false);
        }
        if (taskResolver.matchesDeterministicRule(turn.getQuestion())) {
            PortfolioTask task = taskResolver.resolve(
                    turn.getTurnId(), turn.getQuestion(), turn.getRecommendationContext());
            return execute(withSubjectConstraint(task, turn, content),
                    AnswerIntentSource.RULE, false);
        }
        if (referencesExplicitSubject(turn)) {
            return execute(explicitSubjectTask(turn, content), AnswerIntentSource.RULE, false);
        }
        if (!providerAccess.isAllowed()) {
            return new PortfolioDecision(PortfolioDisposition.NOT_PORTFOLIO, null);
        }
        com.portfolio.agent.answer.intelligence.domain.PortfolioTaskRoutingDecision routed =
                taskResolver.route(
                        turn.getTurnId(), turn.getQuestion(),
                        turn.getRecommendationContext(), true);
        if (routed.isNotPortfolio() || routed.getBoundaryIntent() != null) {
            return new PortfolioDecision(PortfolioDisposition.NOT_PORTFOLIO, null);
        }
        return execute(withSubjectConstraint(routed.getTask(), turn, content),
                AnswerIntentSource.MODEL, false);
    }

    private PortfolioDecision resolveReference(
            PortfolioTurn turn,
            RuntimeAnswerContent content
    ) {
        PortfolioReferenceResolution resolution = referenceContextValidator.validate(
                content, turn.getReferenceContext());
        if (resolution.getType() == PortfolioReferenceResolutionType.INVALID
                || resolution.getType() == PortfolioReferenceResolutionType.REFERENCES_MISSING) {
            return clarification(AnswerIntentSource.REFERENCE, "referenceContext");
        }
        PortfolioTaskMode mode = turn.getReferenceContext().getFollowUpAction()
                == PortfolioFollowUpAction.COMPARE_SUBJECTS
                ? PortfolioTaskMode.COMPARISON
                : PortfolioTaskMode.FACT_LOOKUP;
        PortfolioRetrievalRequest request = retrievalPlanner.planReference(
                turn,
                resolution,
                mode,
                PortfolioConditions.empty());
        PortfolioIntelligenceResult result = material(mode, retriever.retrieve(request))
                .withDecisionMetadata(
                        AnswerIntentSource.REFERENCE,
                        resolution.isContextVersionUpdated());
        return decisionFor(result);
    }

    private PortfolioDecision clarification(
            AnswerIntentSource source,
            String missingCondition
    ) {
        PortfolioIntelligenceResult result = PortfolioIntelligenceResult.clarification(
                        new PortfolioClarification(
                                "Please clarify the portfolio information to use.",
                                missingCondition))
                .withDecisionMetadata(source, false);
        return new PortfolioDecision(PortfolioDisposition.NEEDS_CLARIFICATION, result);
    }

    private PortfolioDecision execute(
            PortfolioTask task,
            AnswerIntentSource source,
            boolean contextVersionUpdated
    ) {
        PortfolioIntelligenceResult result = resolve(task)
                .withDecisionMetadata(source, contextVersionUpdated);
        return decisionFor(result);
    }

    private PortfolioDecision executeContract(PortfolioContractTask task) {
        PortfolioRetrievalResult retrieval = contractEvidenceSelector.select(task);
        PortfolioIntelligenceResult result = material(PortfolioTaskMode.FACT_LOOKUP, retrieval)
                .withDecisionMetadata(AnswerIntentSource.PRESET, false)
                .withContractIdentity(task.getPresetId(), task.getContractVersion());
        if (ContractEvidenceSelector.UNAVAILABLE_NOTICE.equals(result.getNoticeCode())) {
            return new PortfolioDecision(PortfolioDisposition.CAPABILITY_UNAVAILABLE, result);
        }
        return new PortfolioDecision(PortfolioDisposition.ANSWERED, result);
    }

    private PortfolioDecision contractUnavailable(
            String noticeCode,
            String presetId,
            String contractVersion,
            RuntimeAnswerContent content
    ) {
        PortfolioIntelligenceResult result = new PortfolioIntelligenceResult(
                PortfolioTaskMode.FACT_LOOKUP, List.of(), List.of(), null, null,
                content.getContentVersion(), false, noticeCode)
                .withDecisionMetadata(AnswerIntentSource.PRESET, false)
                .withContractIdentity(presetId, contractVersion);
        return new PortfolioDecision(PortfolioDisposition.CAPABILITY_UNAVAILABLE, result);
    }

    private PortfolioDecision decisionFor(PortfolioIntelligenceResult result) {
        if (result.getResolvedIntent() == PortfolioTaskMode.CLARIFICATION_REQUIRED) {
            return new PortfolioDecision(PortfolioDisposition.NEEDS_CLARIFICATION, result);
        }
        if (ContractEvidenceSelector.UNAVAILABLE_NOTICE.equals(result.getNoticeCode())) {
            return new PortfolioDecision(PortfolioDisposition.CAPABILITY_UNAVAILABLE, result);
        }
        if (result.getEvidence().isEmpty()) {
            return new PortfolioDecision(PortfolioDisposition.NOT_SUPPORTED, result);
        }
        return new PortfolioDecision(PortfolioDisposition.ANSWERED, result);
    }

    private PortfolioTask withSubjectConstraint(
            PortfolioTask task,
            PortfolioTurn turn,
            RuntimeAnswerContent content
    ) {
        if (task.getMode() != PortfolioTaskMode.FACT_LOOKUP || task.getSubjectId() != null) {
            return task;
        }
        AnswerKnowledge subject = null;
        if (turn.getProjectSlug() != null) {
            subject = content.getProjects().stream()
                    .filter(candidate -> turn.getProjectSlug().equals(candidate.getSlug()))
                    .findFirst()
                    .orElse(null);
        } else if (turn.getCaseSlug() != null) {
            subject = content.getCases().stream()
                    .filter(candidate -> turn.getCaseSlug().equals(candidate.getSlug()))
                    .findFirst()
                    .orElse(null);
        }
        if (subject == null) {
            return task;
        }
        return new PortfolioTask(
                task.getTurnId(), task.getQuestion(), task.getMode(), task.getConfidence(),
                task.getConditions(), task.getRecommendationContext(), task.getRefinement(),
                subject.getStableId(), task.getPreferredClaimCategories());
    }

    private PortfolioTask explicitSubjectTask(
            PortfolioTurn turn,
            RuntimeAnswerContent content
    ) {
        AnswerKnowledge subject = null;
        if (turn.getProjectSlug() != null) {
            subject = content.getProjects().stream()
                    .filter(candidate -> turn.getProjectSlug().equals(candidate.getSlug()))
                    .findFirst()
                    .orElse(null);
        } else if (turn.getCaseSlug() != null) {
            subject = content.getCases().stream()
                    .filter(candidate -> turn.getCaseSlug().equals(candidate.getSlug()))
                    .findFirst()
                    .orElse(null);
        }
        String subjectId = subject == null
                ? "__missing_explicit_subject__"
                : subject.getStableId();
        return new PortfolioTask(
                turn.getTurnId(),
                turn.getQuestion(),
                PortfolioTaskMode.FACT_LOOKUP,
                1.0d,
                PortfolioConditions.empty(),
                turn.getRecommendationContext(),
                null,
                subjectId);
    }

    private boolean referencesExplicitSubject(PortfolioTurn turn) {
        String question = turn.getQuestion().toLowerCase(Locale.ROOT);
        if (turn.getProjectSlug() != null) {
            return containsAny(
                    question,
                    "\u8fd9\u4e2a\u9879\u76ee",
                    "\u8be5\u9879\u76ee",
                    "\u672c\u9879\u76ee",
                    "this project",
                    "the project");
        }
        if (turn.getCaseSlug() != null) {
            return containsAny(
                    question,
                    "\u8fd9\u4e2a\u6848\u4f8b",
                    "\u8be5\u6848\u4f8b",
                    "\u672c\u6848\u4f8b",
                    "this case",
                    "the case");
        }
        return false;
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    PortfolioIntelligenceResult resolve(PortfolioTask task) {
        Objects.requireNonNull(task, "task");
        PortfolioTaskValidation validation = taskValidator.validate(task);
        if (!validation.isValid()) {
            return PortfolioIntelligenceResult.clarification(validation.getClarification());
        }
        return switch (task.getMode()) {
            case FACT_LOOKUP -> retrieveMaterial(task);
            case COMPARISON -> retrieveMaterial(task);
            case RECOMMENDATION -> recommend(task);
            case REFINE_RECOMMENDATION -> refineRecommendation(task);
            case CLARIFICATION_REQUIRED -> PortfolioIntelligenceResult.clarification(
                    new PortfolioClarification("请说明希望查询、比较或推荐的作品集内容。", "intent"));
        };
    }

    private PortfolioIntelligenceResult retrieveMaterial(PortfolioTask task) {
        PortfolioRetrievalResult retrieval = retrieve(task, task.getMode(), task.getConditions());
        return material(task.getMode(), retrieval);
    }

    private PortfolioIntelligenceResult recommend(PortfolioTask task) {
        PortfolioRetrievalResult retrieval = retrieve(
                task, PortfolioTaskMode.RECOMMENDATION, task.getConditions());
        PortfolioRecommendation recommendation = recommendationPolicy.recommend(
                retrieval.getContentVersion(), task.getConditions(), candidates(retrieval), Set.of());
        return recommendationResult(PortfolioTaskMode.RECOMMENDATION, retrieval, recommendation);
    }

    private PortfolioIntelligenceResult refineRecommendation(PortfolioTask task) {
        PortfolioRecommendationContext context = task.getRecommendationContext();
        PortfolioConditions baseConditions = new PortfolioConditions(
                context.getCareerTrack(), context.getAudienceRole(), context.getCapabilityCodes(), null,
                context.getRequestedSize());
        PortfolioRetrievalRequest validationRequest;
        try {
            validationRequest = PortfolioRetrievalRequest.contextValidation(
                    baseConditions, context.getSelectedPortfolioIds());
        } catch (IllegalArgumentException exception) {
            return invalidRecommendationContext();
        }
        PortfolioRetrievalResult validationRetrieval = retriever.retrieve(validationRequest);
        RecommendationContextValidation contextValidation = contextValidator.validate(
                context, validationRetrieval.getContentVersion(), baseConditions, candidates(validationRetrieval));
        if (!contextValidation.isValid()) {
            return invalidRecommendationContext();
        }
        PortfolioConditions mergedConditions = baseConditions
                .merge(task.getConditions())
                .merge(task.getRefinement().getConditions());
        if (mergedConditions.getAudienceRole() == null) {
            return PortfolioIntelligenceResult.clarification(new PortfolioClarification(
                    "请说明推荐将面向哪类受众。", "audienceRole"));
        }
        PortfolioRetrievalResult recommendationRetrieval = retrieve(
                task, PortfolioTaskMode.REFINE_RECOMMENDATION, mergedConditions);
        PortfolioRecommendation recommendation = recommendationPolicy.recommend(
                recommendationRetrieval.getContentVersion(), mergedConditions,
                candidates(recommendationRetrieval), task.getRefinement().getExcludedPortfolioIds());
        return recommendationResult(
                PortfolioTaskMode.REFINE_RECOMMENDATION, recommendationRetrieval, recommendation);
    }

    private PortfolioIntelligenceResult invalidRecommendationContext() {
        return PortfolioIntelligenceResult.clarification(new PortfolioClarification(
                "当前推荐结果已变化，请重新说明希望推荐的受众。", "recommendationContext"));
    }

    private PortfolioRetrievalResult retrieve(
            PortfolioTask task,
            PortfolioTaskMode mode,
            PortfolioConditions conditions) {
        boolean singleSubjectFactLookup = mode == PortfolioTaskMode.FACT_LOOKUP
                && task.getSubjectId() != null;
        PortfolioRetrievalRequest request = !singleSubjectFactLookup
                ? new PortfolioRetrievalRequest(task.getQuestion(), mode, conditions)
                : PortfolioRetrievalRequest.subjectScope(
                        task.getQuestion(), mode, conditions, task.getSubjectId(),
                        task.getPreferredClaimCategories());
        return retriever.retrieve(request);
    }

    private PortfolioIntelligenceResult material(
            PortfolioTaskMode mode,
            PortfolioRetrievalResult retrieval) {
        return new PortfolioIntelligenceResult(
                mode, retrieval.getSubjects(), retrieval.getPassages(), null, null,
                retrieval.getContentVersion(), retrieval.isDegraded(), retrieval.getNoticeCode());
    }

    private PortfolioIntelligenceResult recommendationResult(
            PortfolioTaskMode mode,
            PortfolioRetrievalResult retrieval,
            PortfolioRecommendation recommendation) {
        return new PortfolioIntelligenceResult(
                mode, retrieval.getSubjects(), retrieval.getPassages(), recommendation, null,
                retrieval.getContentVersion(), retrieval.isDegraded(), retrieval.getNoticeCode());
    }

    private List<SelectionCandidate> candidates(PortfolioRetrievalResult retrieval) {
        Map<String, PortfolioRetrievedSubject> subjectsById = new LinkedHashMap<>();
        for (PortfolioRetrievedSubject subject : retrieval.getSubjects()) {
            PortfolioRetrievedSubject previous = subjectsById.put(subject.getSubjectId(), subject);
            if (previous != null) {
                throw new IllegalArgumentException("retrieval contains duplicate subjectId");
            }
        }
        Map<String, List<PortfolioRetrievedPassage>> passagesBySubject = new LinkedHashMap<>();
        for (PortfolioRetrievedPassage passage : retrieval.getPassages()) {
            if (subjectsById.containsKey(passage.getSubjectId())) {
                passagesBySubject.computeIfAbsent(passage.getSubjectId(), ignored -> new ArrayList<>())
                        .add(passage);
            }
        }
        List<SelectionCandidate> candidates = new ArrayList<>();
        for (PortfolioRetrievedSubject subject : subjectsById.values()) {
            List<PortfolioRetrievedPassage> passages = passagesBySubject.get(subject.getSubjectId());
            if (passages == null || passages.isEmpty() || subject.getCareerTrack() == null) {
                continue;
            }
            candidates.add(candidate(subject, passages));
        }
        candidates.sort(Comparator.comparing(SelectionCandidate::getSubjectId));
        return List.copyOf(candidates);
    }

    private SelectionCandidate candidate(
            PortfolioRetrievedSubject subject,
            List<PortfolioRetrievedPassage> passages) {
        Map<String, EvidenceReference> evidenceByClaimAndId = new LinkedHashMap<>();
        for (PortfolioRetrievedPassage passage : passages) {
            for (com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedEvidenceReference
                    retrievedEvidence : passage.getEvidenceReferences()) {
                String key = passage.getClaimId() + "\u0000" + retrievedEvidence.getEvidenceId();
                evidenceByClaimAndId.putIfAbsent(key, new EvidenceReference(
                        passage.getClaimId(), retrievedEvidence.getEvidenceId(),
                        retrievedEvidence.getLabel(), retrievedEvidence.getPublicStatus()));
            }
        }
        List<EvidenceReference> evidence = new ArrayList<>(evidenceByClaimAndId.values());
        evidence.sort(Comparator.comparing(EvidenceReference::getClaimId)
                .thenComparing(EvidenceReference::getEvidenceId));
        return new SelectionCandidate(
                subject.getSubjectId(), PortfolioSubjectKind.valueOf(subject.getSubjectType()),
                subject.getTitle(), subject.getSummary(), subject.getRoute(), subject.getCareerTrack(),
                subject.getCapabilityCodes(), evidence, subject.getTargetFit(),
                subject.getEvidenceQuality(), subject.getConflictPenalty());
    }
}
