package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendation;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationItem;
import com.portfolio.agent.selection.domain.PortfolioSelection;
import com.portfolio.agent.selection.domain.SelectionCandidate;
import com.portfolio.agent.selection.domain.SelectionTarget;
import com.portfolio.agent.selection.service.ExhaustiveSelectionStrategy;
import com.portfolio.agent.selection.service.SelectionStrategy;
import com.portfolio.agent.selection.service.TopKSelectionStrategy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PortfolioRecommendationPolicy {

    private static final int EXHAUSTIVE_CANDIDATE_LIMIT = 12;

    private final SelectionStrategy exhaustiveStrategy;
    private final SelectionStrategy topKStrategy;
    private final RecommendationBatchFingerprint fingerprint;
    private final int exhaustiveCandidateLimit;

    public PortfolioRecommendationPolicy() {
        this(
                new ExhaustiveSelectionStrategy(),
                new TopKSelectionStrategy(),
                new RecommendationBatchFingerprint(),
                EXHAUSTIVE_CANDIDATE_LIMIT);
    }

    public PortfolioRecommendationPolicy(
            SelectionStrategy exhaustiveStrategy,
            SelectionStrategy topKStrategy,
            RecommendationBatchFingerprint fingerprint,
            int exhaustiveCandidateLimit) {
        this.exhaustiveStrategy = Objects.requireNonNull(exhaustiveStrategy, "exhaustiveStrategy");
        this.topKStrategy = Objects.requireNonNull(topKStrategy, "topKStrategy");
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        if (exhaustiveCandidateLimit < 1) {
            throw new IllegalArgumentException("exhaustiveCandidateLimit must be positive");
        }
        this.exhaustiveCandidateLimit = exhaustiveCandidateLimit;
    }

    public PortfolioRecommendation recommend(
            String contentVersion,
            PortfolioConditions conditions,
            List<SelectionCandidate> candidates,
            Set<String> excludedPortfolioIds) {
        Objects.requireNonNull(conditions, "conditions");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(excludedPortfolioIds, "excludedPortfolioIds");
        List<SelectionCandidate> eligibleCandidates = eligibleCandidates(candidates, conditions, excludedPortfolioIds);
        SelectionTarget target = new SelectionTarget(
                conditions.getCareerTrack(),
                conditions.getAudienceRole(),
                conditions.getCapabilityCodes(),
                conditions.getGoal(),
                conditions.getRequestedSize());
        PortfolioSelection selection = select(target, eligibleCandidates);
        List<String> selectedIds = selection.getSubjectIds();
        String batchId = fingerprint.calculate(contentVersion, conditions, selectedIds);
        PortfolioRecommendationContext context = new PortfolioRecommendationContext(
                batchId,
                contentVersion,
                conditions.getCareerTrack(),
                conditions.getAudienceRole(),
                conditions.getCapabilityCodes(),
                conditions.getRequestedSize(),
                selectedIds);
        List<PortfolioRecommendationItem> items = selection.getCandidates().stream()
                .map(candidate -> item(candidate, conditions))
                .toList();
        return new PortfolioRecommendation(
                batchId,
                context,
                items,
                satisfiedConstraints(selection, target),
                unsatisfiedConstraints(selection, target));
    }

    private List<SelectionCandidate> eligibleCandidates(
            List<SelectionCandidate> candidates,
            PortfolioConditions conditions,
            Set<String> excludedPortfolioIds) {
        Set<String> uniqueIds = new HashSet<>();
        List<SelectionCandidate> eligible = new ArrayList<>();
        for (SelectionCandidate candidate : candidates) {
            Objects.requireNonNull(candidate, "candidates must not contain null");
            if (!uniqueIds.add(candidate.getSubjectId())) {
                throw new IllegalArgumentException("candidates contains duplicate subjectId");
            }
            if (excludedPortfolioIds.contains(candidate.getSubjectId())) {
                continue;
            }
            if (conditions.getCareerTrack() != null
                    && !conditions.getCareerTrack().equals(candidate.getCareerTrack())) {
                continue;
            }
            if (candidate.getEvidenceReferences().stream().noneMatch(reference -> reference.isApproved())) {
                continue;
            }
            eligible.add(candidate);
        }
        eligible.sort(Comparator.comparing(SelectionCandidate::getSubjectId));
        return List.copyOf(eligible);
    }

    private PortfolioSelection select(SelectionTarget target, List<SelectionCandidate> eligibleCandidates) {
        SelectionStrategy strategy = eligibleCandidates.size() <= exhaustiveCandidateLimit
                ? exhaustiveStrategy
                : topKStrategy;
        return strategy.select(target, eligibleCandidates);
    }

    private PortfolioRecommendationItem item(SelectionCandidate candidate, PortfolioConditions conditions) {
        List<String> reasons = new ArrayList<>();
        if (conditions.getCareerTrack() != null
                && conditions.getCareerTrack().equals(candidate.getCareerTrack())) {
            reasons.add("careerTrack:" + conditions.getCareerTrack());
        }
        List<String> matchedCapabilities = candidate.getCapabilityCodes().stream()
                .filter(conditions.getCapabilityCodes()::contains)
                .sorted()
                .toList();
        for (String capability : matchedCapabilities) {
            reasons.add("capability:" + capability);
        }
        List<String> evidenceIds = candidate.getEvidenceReferences().stream()
                .filter(reference -> reference.isApproved())
                .map(reference -> reference.getEvidenceId())
                .sorted()
                .toList();
        return new PortfolioRecommendationItem(
                candidate.getSubjectId(), candidate.getTitle(), candidate.getRoute(), reasons, evidenceIds);
    }

    private List<String> satisfiedConstraints(PortfolioSelection selection, SelectionTarget target) {
        List<String> satisfied = new ArrayList<>();
        if (selection.getCandidates().size() == target.getRequestedSize()) {
            satisfied.add("requestedSize");
        }
        if (selection.getScore().getCapabilityCoverage() == 1.0) {
            satisfied.add("capabilityCodes");
        }
        return List.copyOf(satisfied);
    }

    private List<String> unsatisfiedConstraints(PortfolioSelection selection, SelectionTarget target) {
        List<String> unsatisfied = new ArrayList<>();
        if (selection.getCandidates().size() != target.getRequestedSize()) {
            unsatisfied.add("requestedSize");
        }
        if (selection.getScore().getCapabilityCoverage() != 1.0) {
            unsatisfied.add("capabilityCodes");
        }
        return List.copyOf(unsatisfied);
    }
}
