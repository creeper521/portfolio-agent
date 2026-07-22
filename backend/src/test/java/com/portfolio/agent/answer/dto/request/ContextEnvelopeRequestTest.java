package com.portfolio.agent.answer.dto.request;

import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.FollowUpIntent;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ContextEnvelopeRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void keepsOnlyBoundedStableReferencesAndDefensivelyCopiesLists() {
        List<String> projectSlugs = new ArrayList<>(List.of("sql-audit"));
        List<String> claimIds = new ArrayList<>(List.of("claim-sql-audit-delivered"));

        ContextEnvelopeRequest envelope = new ContextEnvelopeRequest(
                "2026-07-21.1",
                projectSlugs,
                "sql-audit-full-introduction",
                claimIds,
                AnswerSectionType.SOLUTION,
                FollowUpIntent.EXPAND_SECTION);
        projectSlugs.add("unreviewed-project");
        claimIds.add("unreviewed-claim");

        assertThat(envelope.getProjectSlugs()).containsExactly("sql-audit");
        assertThat(envelope.getReferencedClaimIds())
                .containsExactly("claim-sql-audit-delivered");
        assertThat(envelope.toString())
                .contains("EXPAND_SECTION", "projectCount=1", "claimCount=1")
                .doesNotContain("sql-audit", "claim-sql-audit-delivered");
        assertThat(validator.validate(envelope)).isEmpty();
    }

    @Test
    void rejectsDuplicateOrOverBudgetStableReferences() {
        ContextEnvelopeRequest duplicate = new ContextEnvelopeRequest(
                "2026-07-21.1",
                List.of("sql-audit", "sql-audit"),
                null,
                List.of("claim-one", "claim-one"),
                null,
                FollowUpIntent.SHOW_EVIDENCE);
        ContextEnvelopeRequest overBudget = new ContextEnvelopeRequest(
                "2026-07-21.1",
                List.of("project-1", "project-2", "project-3", "project-4", "project-5"),
                null,
                List.of(
                        "claim-1", "claim-2", "claim-3", "claim-4", "claim-5",
                        "claim-6", "claim-7", "claim-8", "claim-9"),
                null,
                FollowUpIntent.COMPARE_PROJECTS);

        Set<ConstraintViolation<ContextEnvelopeRequest>> duplicateViolations =
                validator.validate(duplicate);
        Set<ConstraintViolation<ContextEnvelopeRequest>> budgetViolations =
                validator.validate(overBudget);

        assertThat(duplicateViolations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("stableReferencesUnique");
        assertThat(budgetViolations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("projectSlugs", "referencedClaimIds");
    }

    @Test
    void rejectsMalformedStableIdsAndMissingIntent() {
        ContextEnvelopeRequest envelope = new ContextEnvelopeRequest(
                "bad version with spaces",
                List.of("../private"),
                "INVALID_PRESET",
                List.of("claim/../../private"),
                null,
                null);

        assertThat(validator.validate(envelope))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains(
                        "previousContentVersion",
                        "projectSlugs[0].<list element>",
                        "questionPresetId",
                        "referencedClaimIds[0].<list element>",
                        "followUpIntent");
    }
}
