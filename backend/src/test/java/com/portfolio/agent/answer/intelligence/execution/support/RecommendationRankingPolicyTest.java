package com.portfolio.agent.answer.intelligence.execution.support;

import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.intelligence.execution.domain.PublicEvidenceDescriptor;
import com.portfolio.agent.answer.intelligence.execution.validation.PublicSourceReference;
import com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceUnit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecommendationRankingPolicyTest {
    @Test
    void rankingIsFixedAndCapsEachClaimCategoryAtTwoUnits() {
        List<ValidatedEvidenceUnit> units = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            AnswerClaimProjection claim = new AnswerClaimProjection(
                    "claim-" + index, com.portfolio.agent.answer.domain.AnswerClaimCategory.IMPLEMENTATION,
                    "Statement " + index, "Detail", com.portfolio.agent.answer.domain.AnswerAchievementStatus.IMPLEMENTED_TESTED,
                    com.portfolio.agent.answer.domain.AnswerContributionType.PRIMARY,
                    com.portfolio.agent.answer.domain.AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                    com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus.VERIFIED,
                    com.portfolio.agent.answer.domain.AnswerMateriality.KEY, List.of("evidence-" + index));
            units.add(new ValidatedEvidenceUnit("project-a", claim, new PublicSourceReference(
                    "evidence-" + index, PublicEvidenceDescriptor.SourceType.CODE,
                    "/projects/project-a", "/evidence/evidence-" + index)));
        }

        assertEquals(2, new RecommendationRankingPolicy().rank(units).size());
    }
}
