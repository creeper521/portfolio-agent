package com.portfolio.agent.answer.intelligence.execution.support;

import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceBundle;
import com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceUnit;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Applies closed, deterministic support rules to a validated evidence bundle. */
public final class EvidenceSupportAssessor {
    public EvidenceSupportAssessment assess(SemanticTask task, ValidatedEvidenceBundle bundle) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(bundle, "bundle");
        SemanticTaskParameters parameters = task.getParameters();
        if (parameters instanceof SemanticTaskParameters.PortfolioFact fact) return assessFact(fact, bundle);
        if (parameters instanceof SemanticTaskParameters.PortfolioCompare compare) {
            return assessCompare(compare, bundle);
        }
        if (parameters instanceof SemanticTaskParameters.PortfolioRecommend recommend) {
            return assessRecommendation(recommend, bundle);
        }
        if (parameters instanceof SemanticTaskParameters.PortfolioRefinement) {
            return assessRecommendation(null, bundle);
        }
        return assessment(EvidenceSupportAssessment.SupportStatus.NOT_APPLICABLE, Map.of(), List.of());
    }

    public EvidenceSupportAssessment assessFact(
            SemanticTaskParameters.PortfolioFact fact, ValidatedEvidenceBundle bundle) {
        Map<String, List<ValidatedEvidenceUnit>> byCriterion = new LinkedHashMap<>();
        List<String> omitted = new ArrayList<>();
        boolean any = false;
        boolean all = true;
        for (SemanticRoutingTypes.PortfolioFacet facet : fact.getFacets()) {
            String criterion = fact.getSubject().getSubjectId() + "/" + facet.name();
            List<ValidatedEvidenceUnit> matches = unitsForSubjectAndCategories(
                    bundle, fact.getSubject().getSubjectId(), categoriesFor(facet));
            byCriterion.put(criterion, atMostTwo(matches));
            if (matches.isEmpty()) {
                omitted.add(facet.name());
                all = false;
            } else {
                any = true;
            }
        }
        return assessment(statusFor(all, any), byCriterion, omitted);
    }

    public EvidenceSupportAssessment assessCompare(
            SemanticTaskParameters.PortfolioCompare compare, ValidatedEvidenceBundle bundle) {
        Map<String, List<ValidatedEvidenceUnit>> byCriterion = new LinkedHashMap<>();
        List<String> omitted = new ArrayList<>();
        int comparableDimensions = 0;
        for (SemanticRoutingTypes.ComparisonDimension dimension : compare.getDimensions()) {
            List<ValidatedEvidenceUnit> selected = new ArrayList<>();
            boolean allSubjectsCovered = true;
            for (com.portfolio.agent.answer.routing.domain.SubjectReference subject : compare.getSubjects()) {
                List<ValidatedEvidenceUnit> subjectUnits = unitsForSubjectAndCategories(
                        bundle, subject.getSubjectId(), categoriesFor(dimension));
                if (subjectUnits.isEmpty()) allSubjectsCovered = false;
                else selected.add(subjectUnits.get(0));
            }
            String criterion = dimension.name();
            byCriterion.put(criterion, atMostTwo(selected));
            if (allSubjectsCovered) comparableDimensions++;
            else omitted.add(criterion);
        }
        EvidenceSupportAssessment.SupportStatus status;
        if (comparableDimensions == compare.getDimensions().size() && comparableDimensions > 0) {
            status = EvidenceSupportAssessment.SupportStatus.SUFFICIENT;
        } else if (comparableDimensions > 0) {
            status = EvidenceSupportAssessment.SupportStatus.PARTIAL;
        } else {
            status = EvidenceSupportAssessment.SupportStatus.INSUFFICIENT;
        }
        return assessment(status, byCriterion, omitted);
    }

    public EvidenceSupportAssessment assessRecommendation(
            SemanticTaskParameters.PortfolioRecommend recommendation,
            ValidatedEvidenceBundle bundle) {
        Map<String, List<ValidatedEvidenceUnit>> byCriterion = new LinkedHashMap<>();
        List<ValidatedEvidenceUnit> baseline = new ArrayList<>();
        for (ValidatedEvidenceUnit unit : bundle.getUnits()) {
            if (RecommendationProfiles.baselineCategories().contains(unit.getClaim().getCategory())) {
                baseline.add(unit);
            }
        }
        byCriterion.put(RecommendationProfiles.PUBLIC_DELIVERY_EVIDENCE, atMostTwo(baseline));
        boolean baselinePresent = !baseline.isEmpty();
        List<String> omitted = new ArrayList<>();
        if (!baselinePresent) omitted.add(RecommendationProfiles.PUBLIC_DELIVERY_EVIDENCE);
        if (recommendation != null) {
            for (SemanticRoutingTypes.CapabilityCode capability : recommendation.getCapabilityCodes()) {
                String criterion = capability.name();
                List<ValidatedEvidenceUnit> matches = unitsForCapability(bundle, capability);
                byCriterion.put(criterion, atMostTwo(matches));
                if (matches.isEmpty()) omitted.add(criterion);
            }
        }
        boolean requiredComplete = recommendation == null || recommendation.getCapabilityCodes().stream()
                .allMatch(capability -> !byCriterion.get(capability.name()).isEmpty());
        EvidenceSupportAssessment.SupportStatus status;
        if (baselinePresent && requiredComplete) status = EvidenceSupportAssessment.SupportStatus.SUFFICIENT;
        else if (baselinePresent) status = EvidenceSupportAssessment.SupportStatus.PARTIAL;
        else status = EvidenceSupportAssessment.SupportStatus.INSUFFICIENT;
        return assessment(status, byCriterion, omitted);
    }

    private static List<ValidatedEvidenceUnit> unitsForCapability(
            ValidatedEvidenceBundle bundle, SemanticRoutingTypes.CapabilityCode capability) {
        List<ValidatedEvidenceUnit> result = new ArrayList<>();
        for (ValidatedEvidenceUnit unit : bundle.getUnits()) {
            if (unit.getClaim().getTopics().stream()
                    .anyMatch(topic -> capability.name().equalsIgnoreCase(topic))) result.add(unit);
        }
        return result;
    }

    private static List<ValidatedEvidenceUnit> unitsForSubjectAndCategories(
            ValidatedEvidenceBundle bundle, String subjectId, Set<AnswerClaimCategory> categories) {
        List<ValidatedEvidenceUnit> result = new ArrayList<>();
        for (ValidatedEvidenceUnit unit : bundle.getUnits()) {
            if (subjectId.equals(unit.getSubjectId()) && categories.contains(unit.getClaim().getCategory())) {
                result.add(unit);
            }
        }
        return result;
    }

    private static Set<AnswerClaimCategory> categoriesFor(SemanticRoutingTypes.PortfolioFacet facet) {
        switch (facet) {
            case OVERVIEW: return EnumSet.of(AnswerClaimCategory.BACKGROUND);
            case RESPONSIBILITY: return EnumSet.of(AnswerClaimCategory.RESPONSIBILITY);
            case IMPLEMENTATION: return EnumSet.of(AnswerClaimCategory.IMPLEMENTATION);
            case DECISION: return EnumSet.of(AnswerClaimCategory.TECHNICAL_DECISION);
            case CHALLENGE: return EnumSet.of(AnswerClaimCategory.LIMITATION, AnswerClaimCategory.REFLECTION);
            case INCIDENT: return EnumSet.of(AnswerClaimCategory.VERIFICATION, AnswerClaimCategory.OUTCOME);
            case VERIFICATION: return EnumSet.of(AnswerClaimCategory.VERIFICATION);
            case LIMITATION: return EnumSet.of(AnswerClaimCategory.LIMITATION);
            case LEARNING: return EnumSet.of(AnswerClaimCategory.LEARNING);
            case OUTCOME: return EnumSet.of(AnswerClaimCategory.OUTCOME);
            default: throw new IllegalArgumentException("unsupported facet");
        }
    }

    private static Set<AnswerClaimCategory> categoriesFor(
            SemanticRoutingTypes.ComparisonDimension dimension) {
        switch (dimension) {
            case ARCHITECTURE: return EnumSet.of(AnswerClaimCategory.TECHNICAL_DECISION);
            case IMPLEMENTATION: return EnumSet.of(AnswerClaimCategory.IMPLEMENTATION);
            case DELIVERY: return EnumSet.of(AnswerClaimCategory.RESPONSIBILITY);
            case IMPACT: return EnumSet.of(AnswerClaimCategory.OUTCOME);
            case RISKS: return EnumSet.of(AnswerClaimCategory.LIMITATION);
            case LEARNING: return EnumSet.of(AnswerClaimCategory.LEARNING);
            default: throw new IllegalArgumentException("unsupported dimension");
        }
    }

    private static List<ValidatedEvidenceUnit> atMostTwo(List<ValidatedEvidenceUnit> units) {
        return List.copyOf(units.subList(0, Math.min(2, units.size())));
    }

    private static EvidenceSupportAssessment.SupportStatus statusFor(boolean all, boolean any) {
        if (all && any) return EvidenceSupportAssessment.SupportStatus.SUFFICIENT;
        if (any) return EvidenceSupportAssessment.SupportStatus.PARTIAL;
        return EvidenceSupportAssessment.SupportStatus.INSUFFICIENT;
    }

    private static EvidenceSupportAssessment assessment(
            EvidenceSupportAssessment.SupportStatus status,
            Map<String, List<ValidatedEvidenceUnit>> criteria, List<String> omitted) {
        return new EvidenceSupportAssessment(status, criteria, omitted);
    }
}
