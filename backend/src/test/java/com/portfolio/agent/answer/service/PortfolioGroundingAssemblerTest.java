package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerSubjectType;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationRoute;
import com.portfolio.agent.answer.domain.PortfolioGroundingContext;
import com.portfolio.agent.answer.domain.PortfolioKnowledgeFacet;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioGroundingAssemblerTest {

    @Test
    void implementationQuestionSelectsOnlyImplementationClaimsFromRequestedCase() {
        PortfolioGroundingAssembler assembler = new PortfolioGroundingAssembler(6, 12, 12000);

        PortfolioGroundingContext context = assembler.assemble(
                content(),
                new ConversationRoute(
                        ConversationIntent.PORTFOLIO_GROUNDED,
                        ConversationAnswerScope.PORTFOLIO,
                        0.98,
                        null,
                        "codegraph-evaluation",
                        PortfolioKnowledgeFacet.IMPLEMENTATION,
                        false),
                "这个案例具体怎么实现");

        assertThat(context.getSubject().getSlug()).isEqualTo("codegraph-evaluation");
        assertThat(context.getClaims())
                .extracting(AnswerClaimProjection::getCategory)
                .containsExactly(
                        AnswerClaimCategory.IMPLEMENTATION,
                        AnswerClaimCategory.TECHNICAL_DECISION);
        assertThat(context.getEvidence())
                .extracting(AnswerEvidence::getId)
                .containsExactly("e-implementation", "e-decision");
    }

    private RuntimeAnswerContent content() {
        AnswerKnowledge caseItem = new AnswerKnowledge(
                AnswerSubjectType.CASE,
                "codegraph-evaluation",
                "CodeGraph evaluation",
                "summary",
                "problem",
                List.of("implementation"),
                "solution",
                List.of("decision"),
                List.of("verified"),
                "outcome",
                "limitation",
                "DELIVERED",
                List.of(),
                List.of(
                        evidence("e-implementation"),
                        evidence("e-decision"),
                        evidence("e-outcome")),
                List.of(
                        claim("c-implementation", AnswerClaimCategory.IMPLEMENTATION,
                                "e-implementation"),
                        claim("c-decision", AnswerClaimCategory.TECHNICAL_DECISION,
                                "e-decision"),
                        claim("c-outcome", AnswerClaimCategory.OUTCOME, "e-outcome")));
        return new RuntimeAnswerContent(
                "v1", "hash", List.of(), List.of(caseItem), null, List.of());
    }

    private AnswerClaimProjection claim(
            String id,
            AnswerClaimCategory category,
            String evidenceId
    ) {
        return new AnswerClaimProjection(
                id,
                category,
                "statement " + id,
                "detail " + id,
                AnswerAchievementStatus.DELIVERED,
                AnswerContributionType.PRIMARY,
                AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED,
                AnswerMateriality.KEY,
                List.of("topic"),
                List.of(evidenceId));
    }

    private AnswerEvidence evidence(String id) {
        return new AnswerEvidence(
                id,
                id,
                "DOCUMENT",
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-02"),
                1,
                "summary " + id,
                "APPROVED",
                false);
    }
}
