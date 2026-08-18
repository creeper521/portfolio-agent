package com.portfolio.agent.turn.capability.portfolio.presentation;

import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.turn.capability.portfolio.evidence.PublicSourceReference;
import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit;
import com.portfolio.agent.turn.capability.portfolio.semantic.PortfolioSemanticResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioPresentationComposerTest {
    @Test
    void deterministicPresentationBindsEverySectionToItsPublicSource() {
        AnswerClaimProjection claim = new AnswerClaimProjection(
                "claim-1", AnswerClaimCategory.VERIFICATION,
                "通过回归测试验证。", "detail", AnswerAchievementStatus.IMPLEMENTED_TESTED,
                AnswerContributionType.PRIMARY, AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED, AnswerMateriality.KEY, List.of("evidence-1"));
        ValidatedEvidenceUnit unit = new ValidatedEvidenceUnit("project-a", claim,
                new PublicSourceReference(
                        "E-01", "测试报告", "public-1", "TEST_RESULT",
                        "/projects/project-a", "/evidence/e-01"));
        PortfolioPresentation presentation = new PortfolioPresentationComposer(
                PresentationPolicy.defaults()).compose(
                new PortfolioSemanticResult.Fact(
                        PortfolioSemanticResult.Coverage.FULL,
                        com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope
                                .allPublished("public-1"), List.of(unit), List.of()));
        assertThat(presentation.getSections()).singleElement().satisfies(section -> {
            assertThat(section.getSectionType().name()).isEqualTo("VERIFICATION");
            assertThat(section.getContent()).isEqualTo("通过回归测试验证。");
            assertThat(section.getSources()).extracting(
                    com.portfolio.agent.answer.domain.PublicSourceReferenceValue::getReferenceKey)
                    .containsExactly("E-01");
        });
    }
}
