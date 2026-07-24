package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerRetrievalChunk;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.domain.RetrievalCandidate;
import com.portfolio.agent.answer.domain.RetrievalDecision;
import com.portfolio.agent.answer.domain.RetrievalDecisionType;
import com.portfolio.agent.answer.domain.RetrievalMode;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalContextValidatorTest {

    private final RetrievalContextValidator validator = new RetrievalContextValidator();

    @Test
    void acceptsAKeyClaimWithCurrentDirectEvidence() {
        RetrievalDecision decision = validator.validate(
                List.of(claim("claim-1", AnswerAchievementStatus.DELIVERED,
                        AnswerMateriality.KEY, List.of("evidence-1"))),
                List.of(evidence("evidence-1")),
                Map.of("chunk-1", chunk("chunk-1", "claim-1", 120)),
                List.of(candidate("chunk-1", 0.03)),
                RetrievalMode.HYBRID_ENABLED,
                RetrievalPolicy.firstRelease());

        assertThat(decision.getType()).isEqualTo(RetrievalDecisionType.SUFFICIENT);
        assertThat(decision.getSelectedClaimIds()).containsExactly("claim-1");
        assertThat(decision.getSelectedChunkIds()).containsExactly("chunk-1");
    }

    @Test
    void rejectsMissingEvidenceAndUnsafeContextTruncation() {
        RetrievalDecision missingEvidence = validator.validate(
                List.of(claim("claim-1", AnswerAchievementStatus.DELIVERED,
                        AnswerMateriality.KEY, List.of("missing"))),
                List.of(evidence("evidence-1")),
                Map.of("chunk-1", chunk("chunk-1", "claim-1", 120)),
                List.of(candidate("chunk-1", 0.03)),
                RetrievalMode.KEYWORD_ONLY,
                RetrievalPolicy.firstRelease());
        assertThat(missingEvidence.getType()).isEqualTo(RetrievalDecisionType.INSUFFICIENT);

        RetrievalDecision tooLarge = validator.validate(
                List.of(claim("claim-1", AnswerAchievementStatus.DELIVERED,
                        AnswerMateriality.KEY, List.of("evidence-1"))),
                List.of(evidence("evidence-1")),
                Map.of("chunk-1", chunk("chunk-1", "claim-1", 7000)),
                List.of(candidate("chunk-1", 0.03)),
                RetrievalMode.KEYWORD_ONLY,
                RetrievalPolicy.firstRelease());
        assertThat(tooLarge.getType()).isEqualTo(RetrievalDecisionType.INSUFFICIENT);
    }

    @Test
    void separatesOutOfScopeAmbiguousAndConflictingCandidates() {
        RetrievalDecision outOfScope = validator.validate(
                List.of(), List.of(), Map.of(), List.of(), RetrievalMode.KEYWORD_ONLY,
                RetrievalPolicy.firstRelease());
        assertThat(outOfScope.getType()).isEqualTo(RetrievalDecisionType.OUT_OF_SCOPE);

        List<AnswerClaimProjection> distinctClaims = List.of(
                claim("claim-1", AnswerAchievementStatus.DELIVERED,
                        AnswerMateriality.KEY, List.of("evidence-1")),
                claim("claim-2", AnswerAchievementStatus.DELIVERED,
                        AnswerMateriality.KEY, List.of("evidence-2")));
        RetrievalDecision ambiguous = validator.validate(
                distinctClaims, List.of(evidence("evidence-1"), evidence("evidence-2")),
                Map.of("chunk-1", chunk("chunk-1", "claim-1", 100),
                        "chunk-2", chunk("chunk-2", "claim-2", 100)),
                List.of(candidate("chunk-1", 1, 2, 0.0300),
                        candidate("chunk-2", 2, 1, 0.0297)),
                RetrievalMode.HYBRID_ENABLED, RetrievalPolicy.firstRelease());
        assertThat(ambiguous.getType()).isEqualTo(RetrievalDecisionType.AMBIGUOUS);

        List<AnswerClaimProjection> conflictingClaims = List.of(
                claim("claim-1", AnswerAchievementStatus.DELIVERED,
                        AnswerMateriality.KEY, List.of("evidence-1")),
                claim("claim-2", AnswerAchievementStatus.PLANNED,
                        AnswerMateriality.KEY, List.of("evidence-2")));
        RetrievalDecision conflicting = validator.validate(
                conflictingClaims, List.of(evidence("evidence-1"), evidence("evidence-2")),
                Map.of("chunk-1", chunk("chunk-1", "claim-1", 100),
                        "chunk-2", chunk("chunk-2", "claim-2", 100)),
                List.of(candidate("chunk-1", 0.04), candidate("chunk-2", 0.02)),
                RetrievalMode.HYBRID_ENABLED, RetrievalPolicy.firstRelease());
        assertThat(conflicting.getType()).isEqualTo(RetrievalDecisionType.CONFLICTING);
    }

    @Test
    void acceptsCloseCandidatesWhenKeywordAndVectorAgreeOnTheLeader() {
        List<AnswerClaimProjection> claims = List.of(
                claim("claim-1", AnswerAchievementStatus.DELIVERED,
                        AnswerMateriality.KEY, List.of("evidence-1")),
                claim("claim-2", AnswerAchievementStatus.DELIVERED,
                        AnswerMateriality.KEY, List.of("evidence-2")));

        RetrievalDecision decision = validator.validate(
                claims, List.of(evidence("evidence-1"), evidence("evidence-2")),
                Map.of("chunk-1", chunk("chunk-1", "claim-1", 100),
                        "chunk-2", chunk("chunk-2", "claim-2", 100)),
                List.of(candidate("chunk-1", 1, 1, 0.03278),
                        candidate("chunk-2", 2, 2, 0.03225)),
                RetrievalMode.HYBRID_ENABLED, RetrievalPolicy.firstRelease());

        assertThat(decision.getType()).isEqualTo(RetrievalDecisionType.SUFFICIENT);
    }

    private AnswerClaimProjection claim(
            String id,
            AnswerAchievementStatus status,
            AnswerMateriality materiality,
            List<String> evidenceIds
    ) {
        return new AnswerClaimProjection(
                id, AnswerClaimCategory.OUTCOME, "Statement " + id, "Detail", status,
                AnswerContributionType.PRIMARY, AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED, materiality,
                List.of("DELIVERY"), evidenceIds);
    }

    private AnswerEvidence evidence(String id) {
        return new AnswerEvidence(
                id, "Evidence", "DOCUMENT", LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-20"), 1, "Summary", "APPROVED", false);
    }

    private AnswerRetrievalChunk chunk(String id, String claimId, int textLength) {
        return new AnswerRetrievalChunk(
                id, List.of("sql-audit"), List.of(claimId),
                List.of("DELIVERY"), textLength);
    }

    private RetrievalCandidate candidate(String id, double score) {
        return new RetrievalCandidate(id, 1, 1, score);
    }

    private RetrievalCandidate candidate(
            String id,
            Integer keywordRank,
            Integer vectorRank,
            double score
    ) {
        return new RetrievalCandidate(id, keywordRank, vectorRank, score);
    }
}
