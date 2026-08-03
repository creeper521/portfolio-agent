package com.portfolio.agent.answer.intelligence.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext;
import com.portfolio.agent.selection.domain.EvidenceReference;
import com.portfolio.agent.selection.domain.PortfolioSubjectKind;
import com.portfolio.agent.selection.domain.SelectionCandidate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecommendationContextValidatorTest {

    private final RecommendationBatchFingerprint fingerprint = new RecommendationBatchFingerprint();
    private final RecommendationContextValidator validator = new RecommendationContextValidator(fingerprint);

    @Test
    void rejectsTamperedContextAndChangedContentVersion() {
        PortfolioConditions conditions = conditions();
        PortfolioRecommendationContext valid = context("public-2026-07-31", conditions, List.of("PROJECT-A"));
        PortfolioRecommendationContext tampered = new PortfolioRecommendationContext(
                "rec_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                valid.getContentVersion(), valid.getCareerTrack(), valid.getAudienceRole(),
                valid.getCapabilityCodes(), valid.getRequestedSize(), valid.getSelectedPortfolioIds());

        assertThat(validator.validate(valid, "public-2026-08-01", conditions, List.of(approved("PROJECT-A")))
                .getFailureCode()).isEqualTo(RecommendationContextValidationFailureCode.CONTENT_VERSION_MISMATCH);
        assertThat(validator.validate(tampered, "public-2026-07-31", conditions, List.of(approved("PROJECT-A")))
                .getFailureCode()).isEqualTo(RecommendationContextValidationFailureCode.BATCH_FINGERPRINT_MISMATCH);
    }

    @Test
    void rejectsAnUnavailableOrDuplicateSelectedPortfolioId() {
        PortfolioConditions conditions = conditions();
        PortfolioRecommendationContext unavailable = context(
                "public-2026-07-31", conditions, List.of("PROJECT-MISSING"));
        PortfolioRecommendationContext duplicate = context(
                "public-2026-07-31", conditions, List.of("PROJECT-A", "PROJECT-A"));

        assertThat(validator.validate(unavailable, "public-2026-07-31", conditions, List.of(approved("PROJECT-A")))
                .getFailureCode()).isEqualTo(RecommendationContextValidationFailureCode.SELECTED_PORTFOLIO_ID_NOT_ALLOWED);
        assertThat(validator.validate(duplicate, "public-2026-07-31", conditions,
                List.of(approved("PROJECT-A"))).getFailureCode())
                .isEqualTo(RecommendationContextValidationFailureCode.DUPLICATE_SELECTED_PORTFOLIO_ID);
    }

    @Test
    void rejectsSelectedPortfolioWhenTheCurrentEvidenceGateFails() {
        PortfolioConditions conditions = conditions();
        PortfolioRecommendationContext context = context("public-2026-07-31", conditions, List.of("PROJECT-A"));

        RecommendationContextValidation result = validator.validate(
                context, "public-2026-07-31", conditions, List.of(unapproved("PROJECT-A")));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureCode())
                .isEqualTo(RecommendationContextValidationFailureCode.SELECTED_PORTFOLIO_EVIDENCE_NOT_APPROVED);
    }

    @Test
    void rejectsSelectedPortfolioWhenItHasNoApprovedEvidence() {
        PortfolioConditions conditions = conditions();
        PortfolioRecommendationContext context = context("public-2026-07-31", conditions, List.of("PROJECT-A"));

        RecommendationContextValidation result = validator.validate(
                context, "public-2026-07-31", conditions, List.of(withoutEvidence("PROJECT-A")));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureCode())
                .isEqualTo(RecommendationContextValidationFailureCode.SELECTED_PORTFOLIO_EVIDENCE_NOT_APPROVED);
    }

    private PortfolioConditions conditions() {
        return new PortfolioConditions("JAVA_BACKEND", "INTERVIEWER", Set.of("JAVA"), null, 2);
    }

    private PortfolioRecommendationContext context(
            String contentVersion, PortfolioConditions conditions, List<String> selectedIds) {
        return new PortfolioRecommendationContext(
                fingerprint.calculate(contentVersion, conditions, selectedIds),
                contentVersion,
                conditions.getCareerTrack(),
                conditions.getAudienceRole(),
                conditions.getCapabilityCodes(),
                conditions.getRequestedSize(),
                selectedIds);
    }

    private SelectionCandidate approved(String id) {
        return candidate(id, "APPROVED");
    }

    private SelectionCandidate unapproved(String id) {
        return candidate(id, "PENDING");
    }

    private SelectionCandidate candidate(String id, String evidenceStatus) {
        return new SelectionCandidate(
                id,
                PortfolioSubjectKind.PROJECT,
                "Title " + id,
                "Summary " + id,
                "/projects/" + id.toLowerCase(),
                "JAVA_BACKEND",
                Set.of("JAVA"),
                List.of(new EvidenceReference("claim-" + id, "evidence-" + id, "Evidence", evidenceStatus)),
                0.9,
                0.9,
                0.0);
    }

    private SelectionCandidate withoutEvidence(String id) {
        return new SelectionCandidate(
                id,
                PortfolioSubjectKind.PROJECT,
                "Title " + id,
                "Summary " + id,
                "/projects/" + id.toLowerCase(),
                "JAVA_BACKEND",
                Set.of("JAVA"),
                List.of(),
                0.9,
                0.9,
                0.0);
    }
}
