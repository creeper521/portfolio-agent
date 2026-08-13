package com.portfolio.agent.answer.intelligence.execution.support;

import com.portfolio.agent.answer.domain.AnswerClaimCategory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Closed recommendation profiles compiled from typed P2 parameters. */
public final class RecommendationProfiles {
    public enum ProfileId {
        GENERAL_PORTFOLIO_RECOMMENDATION_V1,
        CAPABILITY_MATCH_RECOMMENDATION_V1
    }

    public static final String PUBLIC_DELIVERY_EVIDENCE = "PUBLIC_DELIVERY_EVIDENCE";

    private RecommendationProfiles() { }

    public static Set<AnswerClaimCategory> baselineCategories() {
        return Set.of(AnswerClaimCategory.RESPONSIBILITY, AnswerClaimCategory.IMPLEMENTATION,
                AnswerClaimCategory.VERIFICATION, AnswerClaimCategory.OUTCOME);
    }

    public static List<AnswerClaimCategory> rankingOrder() {
        return List.of(AnswerClaimCategory.VERIFICATION, AnswerClaimCategory.IMPLEMENTATION,
                AnswerClaimCategory.TECHNICAL_DECISION, AnswerClaimCategory.OUTCOME,
                AnswerClaimCategory.RESPONSIBILITY, AnswerClaimCategory.LEARNING);
    }

    public static Set<AnswerClaimCategory> copyBaselineCategories() {
        return new LinkedHashSet<>(baselineCategories());
    }
}
