package com.portfolio.agent.answer.intelligence.execution.support;

import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceUnit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministic ranking over validated evidence, with a maximum of two units per criterion. */
public final class RecommendationRankingPolicy {
    private final Map<AnswerClaimCategory, Integer> priority;

    public RecommendationRankingPolicy() {
        EnumMap<AnswerClaimCategory, Integer> priorities = new EnumMap<>(AnswerClaimCategory.class);
        int index = 0;
        for (AnswerClaimCategory category : RecommendationProfiles.rankingOrder()) {
            priorities.put(category, index++);
        }
        this.priority = Map.copyOf(priorities);
    }

    public List<ValidatedEvidenceUnit> rank(List<ValidatedEvidenceUnit> units) {
        Objects.requireNonNull(units, "units");
        List<ValidatedEvidenceUnit> ordered = new ArrayList<>(units);
        ordered.sort(Comparator
                .comparingInt((ValidatedEvidenceUnit unit) ->
                        priority.getOrDefault(unit.getClaim().getCategory(), Integer.MAX_VALUE))
                .thenComparing(unit -> unit.getSourceReference().getSubjectRoute())
                .thenComparing(ValidatedEvidenceUnit::getClaimId));
        return List.copyOf(limitPerCategory(ordered));
    }

    private List<ValidatedEvidenceUnit> limitPerCategory(List<ValidatedEvidenceUnit> units) {
        EnumMap<AnswerClaimCategory, Integer> counts = new EnumMap<>(AnswerClaimCategory.class);
        List<ValidatedEvidenceUnit> result = new ArrayList<>();
        for (ValidatedEvidenceUnit unit : units) {
            AnswerClaimCategory category = unit.getClaim().getCategory();
            int count = counts.getOrDefault(category, 0);
            if (count < 2) {
                result.add(unit);
                counts.put(category, count + 1);
            }
        }
        return result;
    }
}
