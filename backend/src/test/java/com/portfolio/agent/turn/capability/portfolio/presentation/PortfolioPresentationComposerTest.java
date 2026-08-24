package com.portfolio.agent.turn.capability.portfolio.presentation;

import com.portfolio.agent.turn.execution.PublicSourceReferenceValue;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerAchievementStatus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimProjection;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimVerificationStatus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerContributionType;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerMateriality;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerVerificationBasis;
import com.portfolio.agent.turn.execution.PublicSourceReferenceValue;
import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit;
import com.portfolio.agent.turn.capability.portfolio.semantic.PortfolioSemanticResult;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioPresentationComposerTest {
    @Test
    void depthControlsVisibleBlockCountAndDetailedContent() {
        List<ValidatedEvidenceUnit> units = List.of(
                unit("1", AnswerClaimCategory.BACKGROUND),
                unit("2", AnswerClaimCategory.IMPLEMENTATION),
                unit("3", AnswerClaimCategory.VERIFICATION));
        PortfolioPresentationComposer composer = new PortfolioPresentationComposer(
                PresentationPolicy.defaults());

        PortfolioPresentation concise = composer.compose(new PortfolioSemanticResult.Fact(
                PortfolioSemanticResult.Coverage.FULL,
                com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope
                        .allPublished("public-1"), units, List.of(),
                UserGoalProposal.Depth.CONCISE));
        PortfolioPresentation detailed = composer.compose(new PortfolioSemanticResult.Fact(
                PortfolioSemanticResult.Coverage.FULL,
                com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope
                        .allPublished("public-1"), units, List.of(),
                UserGoalProposal.Depth.DETAILED));

        assertThat(concise.getSections()).hasSize(2);
        assertThat(concise.getSections()).allSatisfy(section ->
                assertThat(section.getContent()).doesNotContain("详细说明"));
        assertThat(detailed.getSections()).hasSize(3);
        assertThat(detailed.getSections()).allSatisfy(section ->
                assertThat(section.getContent()).contains("详细说明"));
    }

    @Test
    void deterministicPresentationBindsEverySectionToItsPublicSource() {
        AnswerClaimProjection claim = new AnswerClaimProjection(
                "claim-1", AnswerClaimCategory.VERIFICATION,
                "通过回归测试验证。", "detail", AnswerAchievementStatus.IMPLEMENTED_TESTED,
                AnswerContributionType.PRIMARY, AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED, AnswerMateriality.KEY, List.of("evidence-1"));
        ValidatedEvidenceUnit unit = new ValidatedEvidenceUnit("project-a", claim,
                new PublicSourceReferenceValue(
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
                    com.portfolio.agent.turn.execution.PublicSourceReferenceValue::getReferenceKey)
                    .containsExactly("E-01");
        });
    }

    private ValidatedEvidenceUnit unit(String id, AnswerClaimCategory category) {
        AnswerClaimProjection claim = new AnswerClaimProjection(
                "claim-" + id, category, "陈述" + id, "详细说明" + id,
                AnswerAchievementStatus.IMPLEMENTED_TESTED,
                AnswerContributionType.PRIMARY, AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED, AnswerMateriality.KEY,
                List.of("evidence-" + id));
        return new ValidatedEvidenceUnit("project-a", claim,
                new PublicSourceReferenceValue(
                        "E-" + id, "公开来源" + id, "public-1", "TEST_RESULT",
                        "/projects/project-a", "/evidence/e-" + id));
    }
}
