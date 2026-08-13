package com.portfolio.agent.answer.intelligence.execution.resultpolicy;

import com.portfolio.agent.answer.composition.domain.CandidateReference;
import com.portfolio.agent.answer.composition.domain.ExpressionStatement;
import com.portfolio.agent.answer.composition.domain.PortfolioAnswerMaterial;
import com.portfolio.agent.answer.composition.domain.RecommendationAnswerMaterial;
import com.portfolio.agent.answer.composition.domain.RecommendationTier;
import com.portfolio.agent.answer.composition.domain.RefineSource;
import com.portfolio.agent.answer.composition.domain.SubjectReference;
import com.portfolio.agent.answer.composition.domain.SupportTarget;
import com.portfolio.agent.answer.intelligence.execution.domain.CandidateSubject;
import com.portfolio.agent.answer.intelligence.execution.support.EvidenceSupportAssessment;
import com.portfolio.agent.answer.intelligence.execution.support.RecommendationRankingPolicy;
import com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceBundle;
import com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceUnit;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministic candidate order is fixed before P4 composition. */
public final class RecommendationResultPolicy implements PortfolioResultPolicy {
    private final RecommendationRankingPolicy rankingPolicy;
    public RecommendationResultPolicy() { this(new RecommendationRankingPolicy()); }
    public RecommendationResultPolicy(RecommendationRankingPolicy rankingPolicy) {
        this.rankingPolicy = Objects.requireNonNull(rankingPolicy, "rankingPolicy");
    }

    @Override
    public PortfolioAnswerMaterial material(SemanticTask task, ValidatedEvidenceBundle bundle,
            EvidenceSupportAssessment assessment, List<CandidateSubject> publicSubjects) {
        List<ValidatedEvidenceUnit> ranked = rankingPolicy.rank(assessment.getSelectedUnits());
        Map<String, List<com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceUnit>> byId =
                new LinkedHashMap<>();
        ranked.forEach(unit -> byId.computeIfAbsent(unit.getSubjectId(), ignored -> new ArrayList<>()).add(unit));
        Map<String, CandidateSubject> publicById = new LinkedHashMap<>();
        publicSubjects.forEach(subject -> publicById.put(subject.getSubjectId(), subject));
        FactResultPolicy.requireAuthoritativeOwnership(ranked, publicById);
        List<RecommendationAnswerMaterial.RecommendationCandidate> candidates = new ArrayList<>();
        int candidateIndex = 0;
        for (Map.Entry<String, List<com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceUnit>>
                entry : byId.entrySet()) {
            CandidateSubject publicSubject = Objects.requireNonNull(publicById.get(entry.getKey()),
                    "public subject unavailable");
            SubjectReference subject = new SubjectReference(publicSubject.getTitle());
            List<ExpressionStatement> statements = FactResultPolicy.entries(entry.getValue(),
                    Map.of(entry.getKey(), subject), SupportTarget.CRITERION);
            RecommendationAnswerMaterial.RecommendationCriterion criterion =
                    new RecommendationAnswerMaterial.RecommendationCriterion(
                    "APPROVED_EVIDENCE", statements);
            candidates.add(new RecommendationAnswerMaterial.RecommendationCandidate(
                    new CandidateReference(publicSubject.getTitle()),
                    candidateIndex++ == 0 ? RecommendationTier.PRIMARY : RecommendationTier.SECONDARY,
                    List.of(criterion), List.of()));
        }
        if (candidates.isEmpty()) throw new IllegalArgumentException("recommendation candidates unavailable");
        return new RecommendationAnswerMaterial("作品推荐", candidates, List.of(),
                assessment.getOmittedLabels(), RefineSource.NONE);
    }
}
