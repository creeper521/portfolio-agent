package com.portfolio.agent.answer.intelligence.execution.support;

import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationProfilesTest {
    @Test
    void generalProfileUsesControlledOrBaselineAndFixedPriority() {
        assertTrue(RecommendationProfiles.baselineCategories().contains(AnswerClaimCategory.RESPONSIBILITY));
        assertEquals(AnswerClaimCategory.VERIFICATION, RecommendationProfiles.rankingOrder().get(0));
        assertEquals(AnswerClaimCategory.LEARNING,
                RecommendationProfiles.rankingOrder().get(RecommendationProfiles.rankingOrder().size() - 1));
    }
}
