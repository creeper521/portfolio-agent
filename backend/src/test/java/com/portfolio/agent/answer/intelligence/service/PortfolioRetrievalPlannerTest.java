package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioFollowUpAction;
import com.portfolio.agent.answer.intelligence.domain.PortfolioReferenceContext;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalRequest;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalStrategy;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTurn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioRetrievalPlannerTest {

    @Test
    void explicitComparisonKeepsEveryValidatedSubjectAndReferencedClaim() {
        PortfolioReferenceContext reference = new PortfolioReferenceContext(
                "public-1",
                List.of("project-a", "project-b"),
                List.of(),
                null,
                List.of("claim-a", "claim-b"),
                AnswerSectionType.VERIFICATION,
                PortfolioFollowUpAction.COMPARE_SUBJECTS);
        PortfolioTurn turn = PortfolioTurn.builder("turn-1", "Compare their verification")
                .referenceContext(reference)
                .build();
        PortfolioReferenceResolution resolution = PortfolioReferenceResolution.resolved(
                PortfolioReferenceResolutionType.VALID,
                List.of("subject-a", "subject-b"),
                List.of("claim-a", "claim-b"));

        PortfolioRetrievalRequest request = new PortfolioRetrievalPlanner().planReference(
                turn,
                resolution,
                PortfolioTaskMode.COMPARISON,
                PortfolioConditions.empty());

        assertThat(request.getStrategy()).isEqualTo(PortfolioRetrievalStrategy.REFERENCE_SCOPED);
        assertThat(request.getRequiredPortfolioIds())
                .containsExactly("subject-a", "subject-b");
        assertThat(request.getRequiredClaimIds()).containsExactly("claim-a", "claim-b");
        assertThat(request.getPreferredClaimCategories())
                .containsExactly(AnswerClaimCategory.VERIFICATION);
    }
}
