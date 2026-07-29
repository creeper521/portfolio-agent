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
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.DiagnosticLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LocalRetrievalCoordinatorTest {

    @Test
    void successfulKeywordRetrievalPublishesOnlyClosedMetadata() {
        List<DiagnosticEvent> events = new ArrayList<>();

        RetrievalDecision decision = coordinator(
                localText -> {
                    throw new AssertionError("keyword-only must not embed");
                },
                events::add).retrieve(
                "PRIVATE_QUERY_SENTINEL",
                "sql-audit",
                corpus(),
                claims(),
                evidence(),
                RetrievalMode.KEYWORD_ONLY,
                RetrievalPolicy.firstRelease());

        assertThat(decision.getMode()).isEqualTo(RetrievalMode.KEYWORD_ONLY);
        assertThat(events).hasSize(1);
        DiagnosticEvent event = events.getFirst();
        assertThat(event.getName()).isEqualTo("retrieval.completed");
        assertThat(event.getLevel()).isEqualTo(DiagnosticLevel.DEBUG);
        assertThat(event.getFields()).containsOnlyKeys(
                "retrieval.requested_mode",
                "retrieval.actual_mode",
                "retrieval.decision",
                "retrieval.keyword_hit_count",
                "retrieval.vector_hit_count",
                "retrieval.fused_candidate_count",
                "retrieval.accepted_chunk_count",
                "duration.bucket");
        assertThat(event.getFields().toString())
                .doesNotContain(
                        "PRIVATE_QUERY_SENTINEL",
                        "chunk-1",
                        "claim-1",
                        "evidence-1",
                        "similarity");
    }

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
        List<DiagnosticEvent> events = new ArrayList<>();
        LocalEmbeddingPort failing = localText -> {
            calls.incrementAndGet();
            throw new LocalEmbeddingFailureException("LOCAL_INFERENCE_FAILED");
        };

        RetrievalDecision decision = coordinator(failing, events::add).retrieve(
                "SQL 交付", "sql-audit", corpus(), claims(), evidence(),
                RetrievalMode.HYBRID_ENABLED, RetrievalPolicy.firstRelease());

        assertThat(decision.getType()).isEqualTo(RetrievalDecisionType.SUFFICIENT);
        assertThat(decision.getMode()).isEqualTo(RetrievalMode.KEYWORD_FALLBACK);
        assertThat(calls).hasValue(1);
        assertThat(events).hasSize(1);
        DiagnosticEvent event = events.getFirst();
        assertThat(event.getName()).isEqualTo("retrieval.degraded");
        assertThat(event.getFields())
                .containsEntry("retrieval.requested_mode", "HYBRID_ENABLED")
                .containsEntry("retrieval.actual_mode", "KEYWORD_FALLBACK")
                .containsEntry("failure.code", "RETRIEVAL_INFERENCE_FAILED")
                .containsOnlyKeys(
                        "retrieval.requested_mode",
                        "retrieval.actual_mode",
                        "retrieval.decision",
                        "retrieval.keyword_hit_count",
                        "retrieval.vector_hit_count",
                        "retrieval.fused_candidate_count",
                        "retrieval.accepted_chunk_count",
                        "duration.bucket",
                        "failure.code");
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

    @ParameterizedTest
    @CsvSource({
            "LOCAL_INFERENCE_FAILED, RETRIEVAL_INFERENCE_FAILED",
            "MODEL_OUTPUT_SHAPE_INVALID, RETRIEVAL_INFERENCE_FAILED",
            "MODEL_OUTPUT_DIMENSION_INVALID, RETRIEVAL_INFERENCE_FAILED",
            "MODEL_OUTPUT_NON_FINITE, RETRIEVAL_INFERENCE_FAILED",
            "MODEL_OUTPUT_EMPTY, RETRIEVAL_INFERENCE_FAILED",
            "MODEL_OUTPUT_NORM_INVALID, RETRIEVAL_INFERENCE_FAILED",
            "DOCUMENT_TEXT_REQUIRED, RETRIEVAL_INFERENCE_FAILED",
            "VECTOR_DIMENSION_MISMATCH, RETRIEVAL_VECTOR_DIMENSION_MISMATCH",
            "LOCAL_MODEL_DIRECTORY_REQUIRED, RETRIEVAL_MODEL_LOAD_FAILED",
            "LOCAL_MODEL_DIRECTORY_INVALID, RETRIEVAL_MODEL_LOAD_FAILED",
            "LOCAL_MODEL_DESCRIPTOR_MISSING, RETRIEVAL_MODEL_LOAD_FAILED",
            "LOCAL_MODEL_DESCRIPTOR_INVALID, RETRIEVAL_MODEL_LOAD_FAILED",
            "LOCAL_MODEL_ARTIFACT_MISMATCH, RETRIEVAL_MODEL_LOAD_FAILED",
            "TOKENIZER_FILE_MISSING, RETRIEVAL_MODEL_LOAD_FAILED",
            "LOCAL_MODEL_INITIALIZATION_FAILED, RETRIEVAL_MODEL_LOAD_FAILED",
            "MODEL_FILE_MISSING, RETRIEVAL_MODEL_LOAD_FAILED",
            "LOCAL_MODEL_CLOSE_FAILED, RETRIEVAL_MODEL_LOAD_FAILED",
            "LOCAL_EMBEDDING_DISABLED, RETRIEVAL_EMBEDDING_DISABLED"
    })
    void mapsEveryLocalEmbeddingCodeToTypedRetrievalFailure(
            String localCode,
            RetrievalFailureCode expected
    ) {
        LocalEmbeddingFailureException exception =
                new LocalEmbeddingFailureException(localCode);

        assertThat(exception.getCode()).isEqualTo(expected);
        assertThat(exception.getMessage()).isEqualTo(localCode);
    }

    @Test
    void publisherFailureDoesNotChangeRetrievalDecision() {
        RetrievalDecision decision = coordinator(
                localText -> new EmbeddingVector(new float[]{1.0f, 0.0f}),
                event -> {
                    throw new IllegalStateException("publisher unavailable");
                }).retrieve(
                "SQL 交付",
                "sql-audit",
                corpus(),
                claims(),
                evidence(),
                RetrievalMode.HYBRID_ENABLED,
                RetrievalPolicy.firstRelease());

        assertThat(decision.getType()).isEqualTo(RetrievalDecisionType.SUFFICIENT);
        assertThat(decision.getMode()).isEqualTo(RetrievalMode.HYBRID_ENABLED);
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

    private LocalRetrievalCoordinator coordinator(
            LocalEmbeddingPort port,
            DiagnosticEventPublisher publisher
    ) {
        return new LocalRetrievalCoordinator(
                new RetrievalQueryNormalizer(),
                new KeywordRetriever(),
                new VectorRetriever(),
                new ReciprocalRankFusion(),
                new RetrievalContextValidator(),
                port,
                publisher);
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
