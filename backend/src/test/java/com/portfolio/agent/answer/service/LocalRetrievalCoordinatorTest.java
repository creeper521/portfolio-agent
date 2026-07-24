package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerKeywordIndex;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerRetrievalChunk;
import com.portfolio.agent.answer.domain.AnswerRetrievalCorpus;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.domain.AnswerSubjectType;
import com.portfolio.agent.answer.domain.EmbeddingVector;
import com.portfolio.agent.answer.domain.RetrievalDecision;
import com.portfolio.agent.answer.domain.RetrievalDecisionType;
import com.portfolio.agent.answer.domain.RetrievalMode;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import com.portfolio.agent.answer.gateway.LocalEmbeddingPort;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LocalRetrievalCoordinatorTest {

    @Test
    void returnsSufficientHybridDecisionFromLocalEmbedding() {
        AtomicInteger calls = new AtomicInteger();
        LocalEmbeddingPort embedding = localText -> {
            calls.incrementAndGet();
            return new EmbeddingVector(new float[]{1.0f, 0.0f});
        };

        RetrievalDecision decision = coordinator(embedding).retrieve(
                "SQL 交付", "sql-audit", corpus(), claims(), evidence(),
                RetrievalMode.HYBRID_ENABLED, RetrievalPolicy.firstRelease());

        assertThat(decision.getType()).isEqualTo(RetrievalDecisionType.SUFFICIENT);
        assertThat(decision.getMode()).isEqualTo(RetrievalMode.HYBRID_ENABLED);
        assertThat(calls).hasValue(1);
    }

    @Test
    void vectorFailureFallsBackOnceWithoutChangingGroundingGate() {
        AtomicInteger calls = new AtomicInteger();
        LocalEmbeddingPort failing = localText -> {
            calls.incrementAndGet();
            throw new LocalEmbeddingFailureException("LOCAL_INFERENCE_FAILED");
        };

        RetrievalDecision decision = coordinator(failing).retrieve(
                "SQL 交付", "sql-audit", corpus(), claims(), evidence(),
                RetrievalMode.HYBRID_ENABLED, RetrievalPolicy.firstRelease());

        assertThat(decision.getType()).isEqualTo(RetrievalDecisionType.SUFFICIENT);
        assertThat(decision.getMode()).isEqualTo(RetrievalMode.KEYWORD_FALLBACK);
        assertThat(calls).hasValue(1);
    }

    @Test
    void keywordOnlyNeverCallsEmbeddingPort() {
        AtomicInteger calls = new AtomicInteger();
        LocalEmbeddingPort embedding = localText -> {
            calls.incrementAndGet();
            return new EmbeddingVector(new float[]{1.0f, 0.0f});
        };

        RetrievalDecision decision = coordinator(embedding).retrieve(
                "SQL 交付", "sql-audit", corpus(), claims(), evidence(),
                RetrievalMode.KEYWORD_ONLY, RetrievalPolicy.firstRelease());

        assertThat(decision.getType()).isEqualTo(RetrievalDecisionType.SUFFICIENT);
        assertThat(decision.getMode()).isEqualTo(RetrievalMode.KEYWORD_ONLY);
        assertThat(calls).hasValue(0);
    }

    @Test
    void appliesProjectMetadataBeforeTopKSelection() {
        List<AnswerKeywordIndex.DocumentEntry> documents = new java.util.ArrayList<>();
        Map<String, AnswerRetrievalChunk> chunks = new java.util.LinkedHashMap<>();
        for (int index = 0; index < 9; index++) {
            String id = "other-" + index;
            documents.add(new AnswerKeywordIndex.DocumentEntry(
                    id, 10, Map.of("sql", 5, "交付", 5)));
            chunks.put(id, new AnswerRetrievalChunk(
                    id, List.of("other-project"), List.of("other-claim-" + index),
                    List.of("OTHER"), 100));
        }
        documents.add(new AnswerKeywordIndex.DocumentEntry(
                "chunk-1", 2, Map.of("sql", 1, "交付", 1)));
        chunks.put("chunk-1", new AnswerRetrievalChunk(
                "chunk-1", List.of("sql-audit"), List.of("claim-1"),
                List.of("DELIVERY"), 120));
        AnswerRetrievalCorpus mixedCorpus = new AnswerRetrievalCorpus(
                new AnswerKeywordIndex(
                        10, 9.2, documents, Map.of("sql", 10, "交付", 10)),
                Map.of(), chunks);

        RetrievalDecision decision = coordinator(localText -> {
            throw new AssertionError("keyword-only must not embed");
        }).retrieve(
                "SQL 交付", "sql-audit", mixedCorpus, claims(), evidence(),
                RetrievalMode.KEYWORD_ONLY, RetrievalPolicy.firstRelease());

        assertThat(decision.getType()).isEqualTo(RetrievalDecisionType.SUFFICIENT);
        assertThat(decision.getSelectedChunkIds()).containsExactly("chunk-1");
    }

    @Test
    void appliesCaseMetadataWithoutRecallingProjectChunks() {
        AnswerKeywordIndex keywordIndex = new AnswerKeywordIndex(
                2, 2.0,
                List.of(
                        new AnswerKeywordIndex.DocumentEntry(
                                "project-chunk", 2, Map.of("图谱", 2)),
                        new AnswerKeywordIndex.DocumentEntry(
                                "case-chunk", 2, Map.of("图谱", 1))),
                Map.of("图谱", 2));
        AnswerRetrievalCorpus mixedCorpus = new AnswerRetrievalCorpus(
                keywordIndex,
                Map.of(),
                Map.of(
                        "project-chunk", new AnswerRetrievalChunk(
                                "project-chunk", List.of("sql-audit"), List.of(),
                                List.of("project-claim"), List.of("OTHER"), 100),
                        "case-chunk", new AnswerRetrievalChunk(
                                "case-chunk", List.of(), List.of("codegraph-evaluation"),
                                List.of("claim-1"), List.of("DELIVERY"), 120)));

        RetrievalDecision decision = coordinator(localText -> {
            throw new AssertionError("keyword-only must not embed");
        }).retrieve(
                "图谱", "codegraph-evaluation", AnswerSubjectType.CASE,
                mixedCorpus, claims(), evidence(),
                RetrievalMode.KEYWORD_ONLY, RetrievalPolicy.firstRelease());

        assertThat(decision.getType()).isEqualTo(RetrievalDecisionType.SUFFICIENT);
        assertThat(decision.getSelectedChunkIds()).containsExactly("case-chunk");
    }

    private LocalRetrievalCoordinator coordinator(LocalEmbeddingPort port) {
        return new LocalRetrievalCoordinator(
                new RetrievalQueryNormalizer(), new KeywordRetriever(),
                new VectorRetriever(), new ReciprocalRankFusion(),
                new RetrievalContextValidator(), port);
    }

    private AnswerRetrievalCorpus corpus() {
        AnswerKeywordIndex keywordIndex = new AnswerKeywordIndex(
                1, 2.0,
                List.of(new AnswerKeywordIndex.DocumentEntry(
                        "chunk-1", 2, Map.of("sql", 1, "交付", 1))),
                Map.of("sql", 1, "交付", 1));
        return new AnswerRetrievalCorpus(
                keywordIndex,
                Map.of("chunk-1", new float[]{1.0f, 0.0f}),
                Map.of("chunk-1", new AnswerRetrievalChunk(
                        "chunk-1", List.of("sql-audit"), List.of("claim-1"),
                        List.of("DELIVERY"), 120)));
    }

    private List<AnswerClaimProjection> claims() {
        return List.of(new AnswerClaimProjection(
                "claim-1", AnswerClaimCategory.OUTCOME, "Delivered", "Reviewed",
                AnswerAchievementStatus.DELIVERED, AnswerContributionType.PRIMARY,
                AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED, AnswerMateriality.KEY,
                List.of("DELIVERY"), List.of("evidence-1")));
    }

    private List<AnswerEvidence> evidence() {
        return List.of(new AnswerEvidence(
                "evidence-1", "Evidence", "DOCUMENT",
                LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-20"),
                1, "Summary", "APPROVED", false));
    }
}
