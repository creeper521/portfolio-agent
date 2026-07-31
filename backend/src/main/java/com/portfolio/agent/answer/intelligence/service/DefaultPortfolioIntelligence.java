package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.intelligence.domain.PortfolioClarification;
import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioIntelligenceResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendation;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedPassage;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedSubject;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalRequest;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTask;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetriever;
import com.portfolio.agent.selection.domain.EvidenceReference;
import com.portfolio.agent.selection.domain.PortfolioSubjectKind;
import com.portfolio.agent.selection.domain.SelectionCandidate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DefaultPortfolioIntelligence implements PortfolioIntelligence {

    private final PortfolioTaskValidator taskValidator;
    private final PortfolioRetriever retriever;
    private final PortfolioRecommendationPolicy recommendationPolicy;
    private final RecommendationContextValidator contextValidator;

    public DefaultPortfolioIntelligence(
            PortfolioTaskValidator taskValidator,
            PortfolioRetriever retriever,
            PortfolioRecommendationPolicy recommendationPolicy,
            RecommendationContextValidator contextValidator) {
        this.taskValidator = Objects.requireNonNull(taskValidator, "taskValidator");
        this.retriever = Objects.requireNonNull(retriever, "retriever");
        this.recommendationPolicy = Objects.requireNonNull(recommendationPolicy, "recommendationPolicy");
        this.contextValidator = Objects.requireNonNull(contextValidator, "contextValidator");
    }

    @Override
    public PortfolioIntelligenceResult resolve(PortfolioTask task) {
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
        PortfolioRetrievalResult retrieval = retrieve(task.getQuestion(), task.getMode(), task.getConditions());
        return material(task.getMode(), retrieval);
    }

    private PortfolioIntelligenceResult recommend(PortfolioTask task) {
        PortfolioRetrievalResult retrieval = retrieve(
                task.getQuestion(), PortfolioTaskMode.RECOMMENDATION, task.getConditions());
        PortfolioRecommendation recommendation = recommendationPolicy.recommend(
                retrieval.getContentVersion(), task.getConditions(), candidates(retrieval), Set.of());
        return recommendationResult(PortfolioTaskMode.RECOMMENDATION, retrieval, recommendation);
    }

    private PortfolioIntelligenceResult refineRecommendation(PortfolioTask task) {
        PortfolioRecommendationContext context = task.getRecommendationContext();
        PortfolioConditions baseConditions = new PortfolioConditions(
                context.getCareerTrack(), context.getAudienceRole(), context.getCapabilityCodes(), null,
                context.getRequestedSize());
        PortfolioRetrievalResult validationRetrieval = retriever.retrieve(
                PortfolioRetrievalRequest.contextValidation(
                        baseConditions, context.getSelectedPortfolioIds()));
        RecommendationContextValidation contextValidation = contextValidator.validate(
                context, validationRetrieval.getContentVersion(), baseConditions, candidates(validationRetrieval));
        if (!contextValidation.isValid()) {
            return PortfolioIntelligenceResult.clarification(new PortfolioClarification(
                    "当前推荐结果已变化，请重新说明希望推荐的受众。", "recommendationContext"));
        }
        PortfolioConditions mergedConditions = baseConditions
                .merge(task.getConditions())
                .merge(task.getRefinement().getConditions());
        if (mergedConditions.getAudienceRole() == null) {
            return PortfolioIntelligenceResult.clarification(new PortfolioClarification(
                    "请说明推荐将面向哪类受众。", "audienceRole"));
        }
        PortfolioRetrievalResult recommendationRetrieval = retrieve(
                task.getQuestion(), PortfolioTaskMode.REFINE_RECOMMENDATION, mergedConditions);
        PortfolioRecommendation recommendation = recommendationPolicy.recommend(
                recommendationRetrieval.getContentVersion(), mergedConditions,
                candidates(recommendationRetrieval), task.getRefinement().getExcludedPortfolioIds());
        return recommendationResult(
                PortfolioTaskMode.REFINE_RECOMMENDATION, recommendationRetrieval, recommendation);
    }

    private PortfolioRetrievalResult retrieve(
            String question,
            PortfolioTaskMode mode,
            PortfolioConditions conditions) {
        return retriever.retrieve(new PortfolioRetrievalRequest(question, mode, conditions));
    }

    private PortfolioIntelligenceResult material(
            PortfolioTaskMode mode,
            PortfolioRetrievalResult retrieval) {
        return new PortfolioIntelligenceResult(
                mode, retrieval.getSubjects(), retrieval.getPassages(), null, null,
                retrieval.isDegraded(), retrieval.getNoticeCode());
    }

    private PortfolioIntelligenceResult recommendationResult(
            PortfolioTaskMode mode,
            PortfolioRetrievalResult retrieval,
            PortfolioRecommendation recommendation) {
        return new PortfolioIntelligenceResult(
                mode, retrieval.getSubjects(), retrieval.getPassages(), recommendation, null,
                retrieval.isDegraded(), retrieval.getNoticeCode());
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
