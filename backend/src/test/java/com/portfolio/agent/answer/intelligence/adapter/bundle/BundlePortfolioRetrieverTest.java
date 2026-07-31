package com.portfolio.agent.answer.intelligence.adapter.bundle;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerKeywordIndex;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerRetrievalChunk;
import com.portfolio.agent.answer.domain.AnswerRetrievalCorpus;
import com.portfolio.agent.answer.domain.AnswerSubjectType;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.domain.EmbeddingVector;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalRequest;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.answer.service.KeywordRetriever;
import com.portfolio.agent.answer.service.LocalEmbeddingFailureException;
import com.portfolio.agent.answer.service.LocalRetrievalCoordinator;
import com.portfolio.agent.answer.service.ReciprocalRankFusion;
import com.portfolio.agent.answer.service.RetrievalContextValidator;
import com.portfolio.agent.answer.service.RetrievalQueryNormalizer;
import com.portfolio.agent.answer.service.VectorRetriever;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BundlePortfolioRetrieverTest {

    @Test
    void mapsTheChunkSelectedByTheExistingLocalHybridCoordinator() {
        AnswerClaimProjection claim = verifiedClaim("claim-1", "evidence-1");
        AnswerEvidence evidence = approved("evidence-1");
        AnswerKnowledge subject = knowledge(
                "project-1", "sql-audit", "JAVA_BACKEND", Set.of("POSTGRESQL", "RAG"),
                claim, evidence);
        RuntimeAnswerContent content = new RuntimeAnswerContent(
                "public-2026-07-31", "hash", List.of(subject), corpus());
        BundlePortfolioRetriever retriever = new BundlePortfolioRetriever(
                () -> content, coordinator(), RetrievalPolicy.currentRelease());

        PortfolioRetrievalResult result = retriever.retrieve(request());

        assertThat(result.getSubjects())
                .extracting(item -> item.getPortfolioId())
                .containsExactly("project-1");
        assertThat(result.getPassages()).singleElement().satisfies(passage -> {
            assertThat(passage.getPassageId()).isEqualTo("chunk-relevant#claim-1");
            assertThat(passage.getSubjectId()).isEqualTo("project-1");
            assertThat(passage.getClaimId()).isEqualTo("claim-1");
            assertThat(passage.getContent()).isEqualTo("Actual public PostgreSQL claim passage");
            assertThat(passage.getContent()).isNotEqualTo(subject.getSummary());
        });
        assertThat(result.getSubjects().getFirst().getRoute()).isEqualTo("/projects/sql-audit");
        assertThat(result.getSubjects().getFirst().getCapabilityCodes())
                .containsExactlyInAnyOrder("POSTGRESQL", "RAG");
    }

    @Test
    void consumesCareerAndCapabilityConditionsWithoutUsingAudienceAsAnAlgorithmSwitch() {
        AnswerKnowledge subject = knowledge(
                "project-1", "sql-audit", "JAVA_BACKEND", Set.of("POSTGRESQL", "RAG"),
                verifiedClaim("claim-1", "evidence-1"), approved("evidence-1"));
        RuntimeAnswerContent content = new RuntimeAnswerContent(
                "public-2026-07-31", "hash", List.of(subject), corpus());
        BundlePortfolioRetriever retriever = new BundlePortfolioRetriever(
                () -> content, keywordOnlyCoordinator(), RetrievalPolicy.currentRelease());

        PortfolioRetrievalResult matched = retriever.retrieve(new PortfolioRetrievalRequest(
                "showcase", PortfolioTaskMode.RECOMMENDATION,
                new PortfolioConditions(
                        "JAVA_BACKEND", "INTERVIEWER", Set.of("POSTGRESQL"), null, 2), 20));
        PortfolioRetrievalResult careerMismatch = retriever.retrieve(new PortfolioRetrievalRequest(
                "PostgreSQL", PortfolioTaskMode.RECOMMENDATION,
                new PortfolioConditions("AGENT", "INTERVIEWER", Set.of(), null, 2), 20));

        assertThat(matched.getSubjects()).singleElement().satisfies(item -> {
            assertThat(item.getPortfolioId()).isEqualTo("project-1");
            assertThat(item.getCapabilityCodes()).containsExactlyInAnyOrder("POSTGRESQL", "RAG");
        });
        assertThat(careerMismatch.getSubjects()).isEmpty();
    }

    @Test
    void returnsAControlledEmptyResultWhenThePublishedBundleHasNoRetrievalCorpus() {
        RuntimeAnswerContent content = new RuntimeAnswerContent(
                "public-2026-07-31", "hash", List.of(
                knowledge("project-1", verifiedClaim("claim-1", "evidence-1"),
                        approved("evidence-1"))));
        BundlePortfolioRetriever retriever = new BundlePortfolioRetriever(
                () -> content, coordinator(), RetrievalPolicy.currentRelease());

        PortfolioRetrievalResult result = retriever.retrieve(request());

        assertThat(result.getSubjects()).isEmpty();
        assertThat(result.getPassages()).isEmpty();
        assertThat(result.isDegraded()).isTrue();
        assertThat(result.getNoticeCode()).isEqualTo("BUNDLE_RETRIEVAL_UNAVAILABLE");
    }

    private LocalRetrievalCoordinator coordinator() {
        return new LocalRetrievalCoordinator(
                new RetrievalQueryNormalizer(),
                new KeywordRetriever(),
                new VectorRetriever(),
                new ReciprocalRankFusion(),
                new RetrievalContextValidator(),
                text -> new EmbeddingVector(new float[]{1.0f, 0.0f}));
    }

    private LocalRetrievalCoordinator keywordOnlyCoordinator() {
        return new LocalRetrievalCoordinator(
                new RetrievalQueryNormalizer(),
                new KeywordRetriever(),
                new VectorRetriever(),
                new ReciprocalRankFusion(),
                new RetrievalContextValidator(),
                text -> { throw new LocalEmbeddingFailureException("LOCAL_EMBEDDING_DISABLED"); });
    }

    private AnswerRetrievalCorpus corpus() {
        AnswerRetrievalChunk relevant = new AnswerRetrievalChunk(
                "chunk-relevant", List.of("sql-audit"), List.of(), List.of("claim-1"),
                List.of("database"), "Actual public PostgreSQL claim passage", 41);
        AnswerRetrievalChunk irrelevant = new AnswerRetrievalChunk(
                "chunk-irrelevant", List.of("sql-audit"), List.of(), List.of("claim-1"),
                List.of("frontend"), "Unrelated public frontend passage", 32);
        AnswerKeywordIndex keywordIndex = new AnswerKeywordIndex(
                2,
                4.0,
                List.of(
                        new AnswerKeywordIndex.DocumentEntry(
                                "chunk-relevant", 4, Map.of("postgresql", 1)),
                        new AnswerKeywordIndex.DocumentEntry(
                                "chunk-irrelevant", 4, Map.of("frontend", 1))),
                Map.of("postgresql", 1, "frontend", 1));
        return new AnswerRetrievalCorpus(
                keywordIndex,
                Map.of(
                        "chunk-relevant", new float[]{1.0f, 0.0f},
                        "chunk-irrelevant", new float[]{0.0f, 1.0f}),
                Map.of("chunk-relevant", relevant, "chunk-irrelevant", irrelevant));
    }

    private PortfolioRetrievalRequest request() {
        return new PortfolioRetrievalRequest(
                "PostgreSQL", PortfolioTaskMode.FACT_LOOKUP, PortfolioConditions.empty(), 20);
    }

    private AnswerKnowledge knowledge(
            String slug, AnswerClaimProjection claim, AnswerEvidence evidence) {
        return new AnswerKnowledge(
                slug, "Subject title", "Subject summary is not a fact passage", "background",
                List.of(), "solution", List.of(), List.of(), "outcome", "handoff", "DELIVERED",
                List.of(), List.of(evidence), List.of(claim));
    }

    private AnswerKnowledge knowledge(
            String stableId,
            String slug,
            String careerTrack,
            Set<String> capabilityCodes,
            AnswerClaimProjection claim,
            AnswerEvidence evidence) {
        return new AnswerKnowledge(
                AnswerSubjectType.PROJECT, stableId, slug, "Subject title",
                "Subject summary is not a fact passage", "background", List.of(), "solution",
                List.of(), List.of(), "outcome", "handoff", "DELIVERED", careerTrack,
                capabilityCodes, List.of(), List.of(evidence), List.of(claim));
    }

    private AnswerClaimProjection verifiedClaim(String claimId, String evidenceId) {
        return new AnswerClaimProjection(
                claimId, AnswerClaimCategory.OUTCOME, AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED, AnswerMateriality.KEY, List.of(evidenceId));
    }

    private AnswerEvidence approved(String evidenceId) {
        return new AnswerEvidence(
                evidenceId, "Approved public evidence", "REPORT", LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 1), 1, "Public summary", "APPROVED", false);
    }
}
