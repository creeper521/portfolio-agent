package com.portfolio.agent.answer.intelligence.adapter.bundle;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalRequest;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class BundlePortfolioRetrieverTest {

    @Test
    void returnsOnlyVerifiedClaimsWithApprovedEvidenceInStableSubjectOrder() {
        BundlePortfolioRetriever retriever = new BundlePortfolioRetriever(() -> new RuntimeAnswerContent(
                "public-2026-07-31", "hash", List.of(
                knowledge("project-b", verifiedClaim("claim-b", "evidence-b"), approved("evidence-b")),
                knowledge("project-a", verifiedClaim("claim-a", "evidence-a"), approved("evidence-a")),
                knowledge("project-hidden", unverifiedClaim("claim-hidden", "evidence-hidden"),
                        approved("evidence-hidden")))));

        PortfolioRetrievalRequest request = new PortfolioRetrievalRequest(
                "PostgreSQL", PortfolioTaskMode.FACT_LOOKUP, PortfolioConditions.empty(), 20);

        assertThat(retriever.retrieve(request).getSubjects())
                .extracting(subject -> subject.getPortfolioId())
                .containsExactly("project-a", "project-b");
        assertThat(retriever.retrieve(request).getPassages())
                .extracting(passage -> passage.getClaimId())
                .containsExactly("claim-a", "claim-b");
        assertThat(retriever.retrieve(request).getSource().getAdapterId()).isEqualTo("BUNDLE");
    }

    private AnswerKnowledge knowledge(
            String slug, AnswerClaimProjection claim, AnswerEvidence evidence) {
        return new AnswerKnowledge(
                slug, slug, "Public PostgreSQL summary", "background", List.of(), "solution", List.of(),
                List.of(), "outcome", "handoff", "DELIVERED", List.of(), List.of(evidence), List.of(claim));
    }

    private AnswerClaimProjection verifiedClaim(String claimId, String evidenceId) {
        return new AnswerClaimProjection(
                claimId, AnswerClaimCategory.OUTCOME, AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED, AnswerMateriality.KEY, List.of(evidenceId));
    }

    private AnswerClaimProjection unverifiedClaim(String claimId, String evidenceId) {
        return new AnswerClaimProjection(
                claimId, AnswerClaimCategory.OUTCOME, AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.UNVERIFIED, AnswerMateriality.KEY, List.of(evidenceId));
    }

    private AnswerEvidence approved(String evidenceId) {
        return new AnswerEvidence(
                evidenceId, "Approved public evidence", "REPORT", LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 1), 1, "Public summary", "APPROVED", false);
    }
}
