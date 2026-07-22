package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerPlan;
import com.portfolio.agent.answer.domain.AnswerPlanClaim;
import com.portfolio.agent.answer.domain.AnswerQuestion;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.AnswerTurnSnapshot;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.domain.ExpressionTone;
import com.portfolio.agent.answer.domain.ResolvedAnswerContext;
import com.portfolio.agent.answer.dto.request.AnswerRequestSource;
import com.portfolio.agent.answer.dto.request.AudienceRole;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerPlanBuilderTest {

    @Test
    void retrievalPlanContainsOnlySelectedFactsAndDerivedIntent() {
        AnswerEvidence selectedEvidence = new AnswerEvidence(
                "evidence-selected", "Selected evidence", "DOCUMENT",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 20),
                1, "Selected public evidence", "APPROVED", false);
        AnswerClaimProjection selectedClaim = new AnswerClaimProjection(
                "claim-selected", AnswerClaimCategory.OUTCOME,
                "Selected release is delivered.", "Selected handoff is complete.",
                AnswerAchievementStatus.DELIVERED, AnswerContributionType.PRIMARY,
                AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED, AnswerMateriality.KEY,
                List.of("DELIVERY"), List.of("evidence-selected"));
        AnswerClaimProjection unrelatedClaim = new AnswerClaimProjection(
                "claim-unrelated", AnswerClaimCategory.BACKGROUND,
                "Unrelated background.", "Must not enter the retrieval plan.",
                AnswerAchievementStatus.DESIGNED, AnswerContributionType.PRIMARY,
                AnswerVerificationBasis.SELF_DECLARED,
                AnswerClaimVerificationStatus.UNVERIFIED, AnswerMateriality.SUPPORTING,
                List.of("BACKGROUND"), List.of());
        AnswerKnowledge project = new AnswerKnowledge(
                "sql-audit", "SQL Audit", "Whole project summary must not be used",
                "Whole project background must not be used", List.of("Whole responsibility"),
                "Whole solution", List.of("Whole decision"), List.of("Whole verification"),
                "Whole outcome", "Whole handoff", "DELIVERED", List.of(),
                List.of(selectedEvidence), List.of(selectedClaim, unrelatedClaim));
        AnswerTurnSnapshot turn = new AnswerTurnSnapshot(
                "turn", "request", "2026-07-21.1", "sha256:runtime", "sql-audit",
                null, List.of("evidence-selected"), AudienceRole.INTERVIEWER,
                AnswerRequestSource.AGENT_PAGE);
        String rawVisitorText = "raw visitor wording that must stay local";
        ResolvedAnswerContext context = ResolvedAnswerContext.forRetrieval(
                turn, project, "Describe approved OUTCOME facts for sql-audit",
                List.of(selectedClaim), List.of(selectedEvidence));

        AnswerPlan plan = new AnswerPlanBuilder().build(turn, context);

        assertThat(plan.getQuestionPresetId()).isNull();
        assertThat(plan.getCanonicalIntent())
                .isEqualTo("Describe approved OUTCOME facts for sql-audit")
                .doesNotContain(rawVisitorText);
        assertThat(plan.getClaims()).extracting(AnswerPlanClaim::getClaimId)
                .containsExactly("claim-selected");
        assertThat(plan.getRequiredSections()).extracting(section -> section.getType())
                .containsExactly(AnswerSectionType.STATUS);
        assertThat(plan.getRequiredSections().getFirst().getCanonicalContent())
                .contains("Selected release is delivered", "Selected handoff is complete")
                .doesNotContain("Whole", "Unrelated");
    }

    @Test
    void buildsAProviderSafePlanFromCanonicalPublicContentOnly() {
        AnswerEvidence evidence = new AnswerEvidence(
                "evidence-1",
                "Release evidence",
                "DELIVERY_SET",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 20),
                2,
                "Approved release notes and deployment record.",
                "APPROVED",
                false
        );
        AnswerClaimProjection claim = new AnswerClaimProjection(
                "claim-1",
                AnswerClaimCategory.OUTCOME,
                "The approved release is deployed.",
                "Deployment and handoff are complete.",
                AnswerAchievementStatus.DELIVERED,
                AnswerContributionType.PRIMARY,
                AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED,
                AnswerMateriality.KEY,
                List.of("evidence-1")
        );
        AnswerQuestion preset = new AnswerQuestion(
                "preset-1",
                "Describe the approved project",
                List.of("visitor alias that must not leave the resolver"),
                "Approved project overview"
        );
        AnswerKnowledge project = new AnswerKnowledge(
                "project-1",
                "Approved project",
                "Approved public summary",
                "Approved public background",
                List.of("Owned the implementation"),
                "Implemented the approved solution",
                List.of("Selected the bounded design"),
                List.of("Validated the packaged application"),
                "The approved release is deployed",
                "Handoff is complete",
                "DELIVERED",
                List.of(preset),
                List.of(evidence),
                List.of(claim)
        );
        AnswerTurnSnapshot turn = new AnswerTurnSnapshot(
                "memory-turn-id",
                "request-correlation-id",
                "2026-07-21.1",
                "sha256:runtime",
                "project-1",
                "preset-1",
                List.of("evidence-1"),
                AudienceRole.INTERVIEWER,
                AnswerRequestSource.AGENT_PAGE
        );
        ResolvedAnswerContext context = new ResolvedAnswerContext(
                turn, project, preset, List.of(evidence));

        AnswerPlan plan = new AnswerPlanBuilder().build(turn, context);

        assertThat(plan.getContentVersion()).isEqualTo("2026-07-21.1");
        assertThat(plan.getQuestionPresetId()).isEqualTo("preset-1");
        assertThat(plan.getCanonicalIntent()).isEqualTo("Describe the approved project");
        assertThat(plan.getAudienceRole()).isEqualTo(AudienceRole.INTERVIEWER);
        assertThat(plan.getRequiredSections())
                .extracting(section -> section.getType())
                .containsExactly(
                        AnswerSectionType.BACKGROUND,
                        AnswerSectionType.RESPONSIBILITY,
                        AnswerSectionType.SOLUTION,
                        AnswerSectionType.VERIFICATION,
                        AnswerSectionType.STATUS
                );
        assertThat(plan.getClaims())
                .extracting(AnswerPlanClaim::getStatement)
                .containsExactly("The approved release is deployed.");
        assertThat(plan.getEvidence()).extracting(item -> item.getId())
                .containsExactly("evidence-1");
        assertThat(plan.getExpressionPolicy().getTone())
                .isEqualTo(ExpressionTone.CONCISE_TECHNICAL);
    }

    @Test
    void planContractCannotCarryVisitorOrRequestIdentity() {
        assertThat(AnswerPlan.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain(
                        "question",
                        "aliases",
                        "turnId",
                        "requestId",
                        "handoffId",
                        "conversation",
                        "history"
                );
    }
}
