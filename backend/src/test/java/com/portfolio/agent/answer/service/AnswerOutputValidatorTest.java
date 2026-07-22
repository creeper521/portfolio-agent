package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerPlan;
import com.portfolio.agent.answer.domain.AnswerPlanClaim;
import com.portfolio.agent.answer.domain.AnswerPlanEvidence;
import com.portfolio.agent.answer.domain.AnswerPlanSection;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.domain.ExpressionPolicy;
import com.portfolio.agent.answer.domain.ExpressionTone;
import com.portfolio.agent.answer.domain.ModelAnswerDraft;
import com.portfolio.agent.answer.domain.ModelDraftSection;
import com.portfolio.agent.answer.dto.request.AudienceRole;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerOutputValidatorTest {

    private final AnswerOutputValidator validator = new AnswerOutputValidator();

    @Test
    void acceptsACompleteDraftWhoseReferencesAndFactsStayInsideThePlan() {
        AnswerValidationResult result = validator.validate(plan(), validDraft());

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.getAnswer()).isNotNull();
        assertThat(result.getAnswer().getSections()).hasSize(5);
    }

    @Test
    void rejectsTheWholeDraftWhenARequiredSectionIsMissing() {
        ModelAnswerDraft draft = new ModelAnswerDraft(
                "Approved project",
                "The approved release is deployed.",
                validDraft().getSections().subList(0, 4)
        );

        assertThat(validator.validate(plan(), draft).isAccepted()).isFalse();
    }

    @Test
    void rejectsPlanExternalClaimsAndUnlinkedEvidence() {
        List<ModelDraftSection> sections = validDraft().getSections().stream()
                .map(section -> section.getType() == AnswerSectionType.STATUS
                        ? new ModelDraftSection(
                                section.getType(),
                                section.getTitle(),
                                section.getContent(),
                                List.of("evidence-1"),
                                List.of("invented-claim"))
                        : section)
                .toList();
        ModelAnswerDraft unknownClaim = new ModelAnswerDraft(
                "Approved project", "The approved release is deployed.", sections);

        List<ModelDraftSection> unlinkedSections = validDraft().getSections().stream()
                .map(section -> section.getType() == AnswerSectionType.BACKGROUND
                        ? new ModelDraftSection(
                                section.getType(), section.getTitle(), section.getContent(),
                                List.of("evidence-1"), List.of())
                        : section)
                .toList();
        ModelAnswerDraft unlinkedEvidence = new ModelAnswerDraft(
                "Approved project", "The approved release is deployed.", unlinkedSections);

        assertThat(validator.validate(plan(), unknownClaim).isAccepted()).isFalse();
        assertThat(validator.validate(plan(), unlinkedEvidence).isAccepted()).isFalse();
    }

    @Test
    void rejectsInventedNumbersAndExternalActions() {
        ModelAnswerDraft inventedNumber = replaceStatusContent(
                "The approved release is deployed across 999 nodes.");
        ModelAnswerDraft externalUrl = replaceStatusContent(
                "Open https://example.invalid to continue.");
        ModelAnswerDraft toolCall = replaceStatusContent(
                "tool_call: publish_release");

        assertThat(validator.validate(plan(), inventedNumber).isAccepted()).isFalse();
        assertThat(validator.validate(plan(), externalUrl).isAccepted()).isFalse();
        assertThat(validator.validate(plan(), toolCall).isAccepted()).isFalse();
    }

    private ModelAnswerDraft replaceStatusContent(String content) {
        List<ModelDraftSection> sections = validDraft().getSections().stream()
                .map(section -> section.getType() == AnswerSectionType.STATUS
                        ? new ModelDraftSection(
                                section.getType(), section.getTitle(), content,
                                section.getEvidenceIds(), section.getClaimIds())
                        : section)
                .toList();
        return new ModelAnswerDraft("Approved project", "Approved public summary", sections);
    }

    private ModelAnswerDraft validDraft() {
        return new ModelAnswerDraft(
                "Approved project",
                "The approved release is deployed.",
                List.of(
                        section(AnswerSectionType.BACKGROUND, "Project background",
                                "Approved public background", List.of(), List.of()),
                        section(AnswerSectionType.RESPONSIBILITY, "My responsibility",
                                "Owned the implementation", List.of(), List.of()),
                        section(AnswerSectionType.SOLUTION, "Technical approach",
                                "Implemented the approved solution", List.of(), List.of()),
                        section(AnswerSectionType.VERIFICATION, "Verification",
                                "Validated the packaged application", List.of(), List.of()),
                        section(AnswerSectionType.STATUS, "Status",
                                "The approved release is deployed.",
                                List.of("evidence-1"), List.of("claim-1"))
                )
        );
    }

    private ModelDraftSection section(
            AnswerSectionType type,
            String title,
            String content,
            List<String> evidenceIds,
            List<String> claimIds
    ) {
        return new ModelDraftSection(type, title, content, evidenceIds, claimIds);
    }

    private AnswerPlan plan() {
        AnswerPlanClaim claim = new AnswerPlanClaim(
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
        AnswerPlanEvidence evidence = new AnswerPlanEvidence(
                "evidence-1", "Release evidence", "DELIVERY_SET",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 20), 2,
                "Approved release notes and deployment record."
        );
        return new AnswerPlan(
                "2026-07-21.1",
                "preset-1",
                "Describe the approved project",
                AudienceRole.INTERVIEWER,
                "Approved project",
                "Approved public summary",
                List.of(
                        planSection(AnswerSectionType.BACKGROUND, "Project background",
                                "Approved public background", List.of(), List.of()),
                        planSection(AnswerSectionType.RESPONSIBILITY, "My responsibility",
                                "Owned the implementation", List.of(), List.of()),
                        planSection(AnswerSectionType.SOLUTION, "Technical approach",
                                "Implemented the approved solution", List.of(), List.of()),
                        planSection(AnswerSectionType.VERIFICATION, "Verification",
                                "Validated the packaged application", List.of(), List.of()),
                        planSection(AnswerSectionType.STATUS, "Status",
                                "The approved release is deployed.",
                                List.of("claim-1"), List.of("evidence-1"))
                ),
                List.of(claim),
                List.of(evidence),
                new ExpressionPolicy(
                        ExpressionTone.CONCISE_TECHNICAL,
                        120, 250, 80, 1000, true, true,
                        List.of(AnswerClaimCategory.OUTCOME)
                )
        );
    }

    private AnswerPlanSection planSection(
            AnswerSectionType type,
            String title,
            String content,
            List<String> claimIds,
            List<String> evidenceIds
    ) {
        return new AnswerPlanSection(type, title, content, claimIds, evidenceIds);
    }
}
