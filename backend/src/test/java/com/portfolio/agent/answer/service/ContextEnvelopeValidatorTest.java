package com.portfolio.agent.answer.service;

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
import com.portfolio.agent.answer.domain.ContextResolution;
import com.portfolio.agent.answer.domain.ContextResolutionType;
import com.portfolio.agent.answer.domain.FollowUpIntent;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.dto.request.ContextEnvelopeRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextEnvelopeValidatorTest {

    private final ContextEnvelopeValidator validator = new ContextEnvelopeValidator();

    @Test
    void resolvesExactStableReferencesWithinTheSameVersion() {
        RuntimeAnswerContent content = content("2026-07-21.1", "sql-audit", "claim-solution");

        ContextResolution resolution = validator.validate(content, envelope(
                "2026-07-21.1", "sql-audit", "claim-solution"));

        assertThat(resolution.getType()).isEqualTo(ContextResolutionType.VALID);
        assertThat(resolution.getEnvelope().orElseThrow().getProjectSlugs())
                .containsExactly("sql-audit");
        assertThat(resolution.getEnvelope().orElseThrow().getReferencedClaimIds())
                .containsExactly("claim-solution");
    }

    @Test
    void marksVersionUpdateOnlyWhenEveryStableReferenceStillResolves() {
        RuntimeAnswerContent content = content("2026-07-22.1", "sql-audit", "claim-solution");

        ContextResolution resolution = validator.validate(content, envelope(
                "2026-07-21.1", "sql-audit", "claim-solution"));

        assertThat(resolution.getType()).isEqualTo(ContextResolutionType.VERSION_UPDATED);
        assertThat(resolution.getEnvelope().orElseThrow().getContentVersion())
                .isEqualTo("2026-07-22.1");
    }

    @Test
    void invalidatesRemovedMovedOrSemanticallyIncompatibleReferences() {
        RuntimeAnswerContent current = content(
                "2026-07-22.1", "portfolio-agent", "claim-solution");
        RuntimeAnswerContent changedCategory = contentWithCategory(
                "2026-07-22.1", "sql-audit", "claim-solution", AnswerClaimCategory.OUTCOME);

        assertThat(validator.validate(current, envelope(
                "2026-07-21.1", "sql-audit", "claim-solution")).getType())
                .isEqualTo(ContextResolutionType.INVALID);
        assertThat(validator.validate(changedCategory, envelope(
                "2026-07-21.1", "sql-audit", "claim-solution")).getType())
                .isEqualTo(ContextResolutionType.INVALID);
        assertThat(validator.validate(
                content("2026-07-22.1", "sql-audit", "claim-other"),
                envelope("2026-07-21.1", "sql-audit", "claim-solution")).getType())
                .isEqualTo(ContextResolutionType.INVALID);
    }

    private ContextEnvelopeRequest envelope(
            String version,
            String projectSlug,
            String claimId
    ) {
        return new ContextEnvelopeRequest(
                version,
                List.of(projectSlug),
                "question-overview",
                List.of(claimId),
                AnswerSectionType.SOLUTION,
                FollowUpIntent.EXPAND_SECTION);
    }

    private RuntimeAnswerContent content(String version, String projectSlug, String claimId) {
        return contentWithCategory(
                version, projectSlug, claimId, AnswerClaimCategory.IMPLEMENTATION);
    }

    private RuntimeAnswerContent contentWithCategory(
            String version,
            String projectSlug,
            String claimId,
            AnswerClaimCategory category
    ) {
        AnswerClaimProjection claim = new AnswerClaimProjection(
                claimId, category, "statement", "detail",
                AnswerAchievementStatus.DELIVERED,
                AnswerContributionType.PRIMARY,
                AnswerVerificationBasis.SELF_DECLARED,
                AnswerClaimVerificationStatus.UNVERIFIED,
                AnswerMateriality.KEY,
                List.of("TECHNICAL_DECISIONS"), List.of());
        AnswerQuestion question = new AnswerQuestion(
                "question-overview", "Overview", List.of(), "Overview");
        AnswerKnowledge project = new AnswerKnowledge(
                projectSlug, projectSlug, "summary", "background",
                List.of("responsibility"), "solution", List.of("decision"),
                List.of("verification"), "outcome", "handoff", "DELIVERED",
                List.of(question), List.of(), List.of(claim));
        return new RuntimeAnswerContent(version, "sha256:runtime", List.of(project));
    }
}
