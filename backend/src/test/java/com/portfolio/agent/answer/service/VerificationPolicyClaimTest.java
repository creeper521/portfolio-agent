package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerSection;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.GeneratedAnswer;
import com.portfolio.agent.answer.domain.ResolvedAnswerContext;
import com.portfolio.agent.answer.domain.VerificationStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationPolicyClaimTest {

    private final VerificationPolicy policy = new VerificationPolicy();

    @Test
    void verifiesOnlyWhenEveryReferencedKeyClaimHasEvidenceSupportedDirectEvidence() {
        AnswerClaimProjection claim = claim("claim-key", AnswerMateriality.KEY,
                AnswerVerificationBasis.EVIDENCE_SUPPORTED, AnswerClaimVerificationStatus.VERIFIED,
                List.of("evidence-1"));

        assertThat(verify(List.of(claim), List.of("claim-key"), List.of("evidence-1")))
                .isEqualTo(VerificationStatus.VERIFIED);
    }

    @Test
    void unsupportedKeyClaimCannotBeMaskedByVerifiedSupportingClaim() {
        AnswerClaimProjection key = claim("claim-key", AnswerMateriality.KEY,
                AnswerVerificationBasis.UNSUPPORTED, AnswerClaimVerificationStatus.UNVERIFIED, List.of());
        AnswerClaimProjection supporting = claim("claim-supporting", AnswerMateriality.SUPPORTING,
                AnswerVerificationBasis.EVIDENCE_SUPPORTED, AnswerClaimVerificationStatus.VERIFIED,
                List.of("evidence-1"));

        assertThat(verify(List.of(key, supporting),
                List.of("claim-key", "claim-supporting"), List.of("evidence-1")))
                .isEqualTo(VerificationStatus.UNVERIFIED);
    }

    @Test
    void selfDeclaredKeyClaimCapsAnswerAtPartiallyVerified() {
        AnswerClaimProjection claim = claim("claim-key", AnswerMateriality.KEY,
                AnswerVerificationBasis.SELF_DECLARED, AnswerClaimVerificationStatus.PARTIALLY_VERIFIED,
                List.of());

        assertThat(verify(List.of(claim), List.of("claim-key"), List.of()))
                .isEqualTo(VerificationStatus.PARTIALLY_VERIFIED);
    }

    private VerificationStatus verify(
            List<AnswerClaimProjection> claims,
            List<String> claimIds,
            List<String> evidenceIds
    ) {
        AnswerKnowledge project = new AnswerKnowledge(
                "sql-audit", "SQL Audit", "Summary", "Background", List.of("Responsibility"),
                "Solution", List.of("Decision"), List.of("Verified"), "Outcome", "Handoff",
                "DELIVERED", List.of(), List.of(), claims);
        ResolvedAnswerContext context = new ResolvedAnswerContext(null, project, null, List.of());
        GeneratedAnswer answer = new GeneratedAnswer("Title", "Summary", List.of(
                new AnswerSection(AnswerSectionType.STATUS, "Status", "Content", evidenceIds, claimIds)
        ));
        return policy.verify(AnswerResolution.ANSWERED, context, answer);
    }

    private AnswerClaimProjection claim(
            String id,
            AnswerMateriality materiality,
            AnswerVerificationBasis basis,
            AnswerClaimVerificationStatus status,
            List<String> evidenceIds
    ) {
        return new AnswerClaimProjection(
                id, AnswerClaimCategory.OUTCOME, basis, status, materiality, evidenceIds);
    }
}
