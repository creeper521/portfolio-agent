package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerQuestion;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.intelligence.domain.PortfolioFollowUpAction;
import com.portfolio.agent.answer.intelligence.domain.PortfolioReferenceContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioReferenceContextValidatorTest {

    private final PortfolioReferenceContextValidator validator =
            new PortfolioReferenceContextValidator();

    @Test
    void validatesAllStableReferencesAgainstOnePublicSnapshot() {
        PortfolioReferenceResolution result = validator.validate(
                content("public-1", "claim-a"),
                reference("public-1", "claim-a"));

        assertThat(result.getType()).isEqualTo(PortfolioReferenceResolutionType.VALID);
        assertThat(result.getSubjectIds()).containsExactly("project-a");
        assertThat(result.getClaimIds()).containsExactly("claim-a");
        assertThat(result.isContextVersionUpdated()).isFalse();
    }

    @Test
    void marksVersionUpdatedOnlyWhenEveryReferenceStillExists() {
        PortfolioReferenceResolution result = validator.validate(
                content("public-2", "claim-a"),
                reference("public-1", "claim-a"));

        assertThat(result.getType())
                .isEqualTo(PortfolioReferenceResolutionType.VERSION_UPDATED);
        assertThat(result.isContextVersionUpdated()).isTrue();
    }

    @Test
    void rejectsPartiallyStaleReferencesInsteadOfSilentlyDroppingThem() {
        PortfolioReferenceContext context = new PortfolioReferenceContext(
                "public-1",
                List.of("project-a"),
                List.of(),
                "preset-a",
                List.of("claim-a", "claim-missing"),
                AnswerSectionType.VERIFICATION,
                PortfolioFollowUpAction.SHOW_EVIDENCE);

        PortfolioReferenceResolution result = validator.validate(
                content("public-2", "claim-a"), context);

        assertThat(result.getType()).isEqualTo(PortfolioReferenceResolutionType.INVALID);
    }

    @Test
    void asksForClarificationWhenEveryReferencedSubjectDisappeared() {
        PortfolioReferenceResolution result = validator.validate(
                new RuntimeAnswerContent("public-2", "sha256:runtime", List.of()),
                reference("public-1", "claim-a"));

        assertThat(result.getType())
                .isEqualTo(PortfolioReferenceResolutionType.REFERENCES_MISSING);
    }

    private PortfolioReferenceContext reference(String version, String claimId) {
        return new PortfolioReferenceContext(
                version,
                List.of("project-a"),
                List.of(),
                "preset-a",
                List.of(claimId),
                AnswerSectionType.VERIFICATION,
                PortfolioFollowUpAction.SHOW_EVIDENCE);
    }

    private RuntimeAnswerContent content(String version, String claimId) {
        AnswerClaimProjection claim = new AnswerClaimProjection(
                claimId,
                AnswerClaimCategory.VERIFICATION,
                "statement",
                "detail",
                AnswerAchievementStatus.DELIVERED,
                AnswerContributionType.PRIMARY,
                AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED,
                AnswerMateriality.KEY,
                List.of("evidence-a"));
        AnswerKnowledge project = new AnswerKnowledge(
                "project-a",
                "Project A",
                "Summary",
                "Background",
                List.of("Responsibility"),
                "Solution",
                List.of("Decision"),
                List.of("Verification"),
                "Outcome",
                "Handoff",
                "ACTIVE",
                List.of(new AnswerQuestion(
                        "preset-a", "Question A", List.of(), "Question A")),
                List.of(),
                List.of(claim));
        return new RuntimeAnswerContent(version, "sha256:runtime", List.of(project));
    }
}
