package com.portfolio.agent.turn.capability.portfolio.retrieval;

import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.CandidateRetrievalResult;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.RetrievalMode;
import com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope;
import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceInvocation;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerAchievementStatus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimProjection;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimVerificationStatus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerContributionType;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerEvidence;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerKeywordIndex;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerKnowledge;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerMateriality;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerRetrievalChunk;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerRetrievalCorpus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerSubjectType;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerVerificationBasis;
import com.portfolio.agent.turn.capability.portfolio.knowledge.PortfolioKnowledgeGateway;
import com.portfolio.agent.turn.capability.portfolio.knowledge.RuntimeAnswerContent;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.PostgresKnowledgeQuery;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.PostgresKnowledgeQueryResult;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.planning.SemanticTask;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PortfolioRetrieverAdapterTest {

    @Test
    void directAdaptersReturnOnlyTheFinalCandidateModel() {
        PortfolioKnowledgeGateway bundleGateway = mock(PortfolioKnowledgeGateway.class);
        when(bundleGateway.getContent()).thenReturn(
                new RuntimeAnswerContent("public-1", "hash-1", List.of()));
        RetrievalAttemptResult bundle = new BundlePortfolioRetrieverAdapter(
                bundleGateway, text -> { throw new AssertionError("exact retrieval must not embed"); }, false)
                .retrieve(
                invocation(CorpusBackend.BUNDLE),
                new RetrievalRequest(CorpusBackend.BUNDLE, SearchStrategy.EXACT),
                activeDeadline());

        PostgresKnowledgeQuery postgresQuery = (invocation, request) ->
                new PostgresKnowledgeQueryResult(
                        new CandidateRetrievalResult(
                                "public-1", RetrievalMode.FTS_ONLY, List.of()),
                        List.of());
        RetrievalAttemptResult postgres = new PostgresPortfolioRetrieverAdapter(postgresQuery).retrieve(
                invocation(CorpusBackend.POSTGRESQL),
                new RetrievalRequest(CorpusBackend.POSTGRESQL, SearchStrategy.EXACT),
                activeDeadline());

        assertThat(bundle.getCandidateSet().orElseThrow().getContentReleaseId())
                .isEqualTo("public-1");
        assertThat(postgres.getCandidateSet().orElseThrow().getContentReleaseId())
                .isEqualTo("public-1");
    }

    @Test
    void expiredDeadlinePreventsIoAndReleaseMismatchFailsClosed() {
        PortfolioKnowledgeGateway untouched = mock(PortfolioKnowledgeGateway.class);
        RetrievalAttemptResult cancelled = new BundlePortfolioRetrieverAdapter(
                untouched, text -> { throw new AssertionError("expired retrieval must not embed"); }, false)
                .retrieve(
                invocation(CorpusBackend.BUNDLE),
                new RetrievalRequest(CorpusBackend.BUNDLE, SearchStrategy.EXACT),
                new TurnDeadline(Instant.parse("2026-08-18T00:00:00Z"), clock()));

        assertThat(cancelled.getFailure()).contains(RetrievalAttemptFailure.CANCELLED);
        verifyNoInteractions(untouched);

        PortfolioKnowledgeGateway mismatched = mock(PortfolioKnowledgeGateway.class);
        when(mismatched.getContent()).thenReturn(
                new RuntimeAnswerContent("public-2", "hash-2", List.of()));
        RetrievalAttemptResult failed = new BundlePortfolioRetrieverAdapter(
                mismatched, text -> { throw new AssertionError("mismatched release must not embed"); }, false)
                .retrieve(
                invocation(CorpusBackend.BUNDLE),
                new RetrievalRequest(CorpusBackend.BUNDLE, SearchStrategy.EXACT),
                activeDeadline());

        assertThat(failed.getFailure()).contains(RetrievalAttemptFailure.INTEGRITY_FAILURE);
    }

    @Test
    void bundleKeywordAndHybridStrategiesRankInsteadOfUsingBundleOrder() {
        PortfolioKnowledgeGateway gateway = () -> rankedContent();
        RetrievalAttemptResult keyword = new BundlePortfolioRetrieverAdapter(
                gateway, text -> { throw new AssertionError("keyword retrieval must not embed"); }, false)
                .retrieve(
                        invocation(SearchStrategy.KEYWORD),
                        new RetrievalRequest(CorpusBackend.BUNDLE, SearchStrategy.KEYWORD),
                        activeDeadline());
        assertThat(keyword.getCandidateSet().orElseThrow().getSubjects())
                .extracting(CandidateSubject::getSubjectId)
                .containsExactly("project-b", "project-a");
        assertThat(keyword.getCandidateSet().orElseThrow().getSubjects().getFirst())
                .satisfies(subject -> {
                    assertThat(subject.getCareerTrack()).isEqualTo("BACKEND");
                    assertThat(subject.getCapabilityCodes()).containsExactly("JAVA");
                });

        AtomicBoolean embedded = new AtomicBoolean();
        RetrievalAttemptResult hybrid = new BundlePortfolioRetrieverAdapter(
                gateway,
                text -> {
                    embedded.set(true);
                    return new com.portfolio.agent.infrastructure.retrieval.EmbeddingVector(
                            new float[]{1.0f, 0.0f});
                },
                true)
                .retrieve(
                        invocation(SearchStrategy.HYBRID),
                        new RetrievalRequest(CorpusBackend.BUNDLE, SearchStrategy.HYBRID),
                        activeDeadline());
        assertThat(embedded).isTrue();
        assertThat(hybrid.getCandidateSet().orElseThrow().getSubjects())
                .extracting(CandidateSubject::getSubjectId)
                .containsExactly("project-a", "project-b");
    }

    private PortfolioEvidenceInvocation invocation(CorpusBackend backend) {
        return new PortfolioEvidenceInvocation(
                SemanticTask.Type.PORTFOLIO_FACT,
                AuthorizedSubjectScope.allPublished("public-1"),
                List.of(PortfolioEvidenceInvocation.FacetProfile.BACKGROUND),
                List.of(),
                "public-1",
                backend,
                SearchStrategy.EXACT,
                null,
                null);
    }

    private PortfolioEvidenceInvocation invocation(SearchStrategy strategy) {
        return new PortfolioEvidenceInvocation(
                SemanticTask.Type.PORTFOLIO_RECOMMEND,
                AuthorizedSubjectScope.allPublished("public-1"),
                List.of(PortfolioEvidenceInvocation.FacetProfile.RECOMMENDATION),
                List.of(),
                "public-1",
                CorpusBackend.BUNDLE,
                strategy,
                null,
                null);
    }

    private RuntimeAnswerContent rankedContent() {
        AnswerKnowledge first = knowledge("project-a", "project-a", "claim-a", "evidence-a");
        AnswerKnowledge second = knowledge("project-b", "project-b", "claim-b", "evidence-b");
        Map<String, AnswerRetrievalChunk> chunks = new LinkedHashMap<>();
        chunks.put("chunk-a", new AnswerRetrievalChunk(
                "chunk-a", List.of("project-a"), List.of(), List.of("claim-a"),
                List.of("实现"), "项目 A 实现", 2));
        chunks.put("chunk-b", new AnswerRetrievalChunk(
                "chunk-b", List.of("project-b"), List.of(), List.of("claim-b"),
                List.of("实现"), "项目 B 实现", 2));
        AnswerKeywordIndex keywordIndex = new AnswerKeywordIndex(
                2,
                2.0d,
                List.of(
                        new AnswerKeywordIndex.DocumentEntry(
                                "chunk-a", 2, Map.of("实现", 1)),
                        new AnswerKeywordIndex.DocumentEntry(
                                "chunk-b", 2, Map.of("实现", 4))),
                Map.of("实现", 2));
        AnswerRetrievalCorpus corpus = new AnswerRetrievalCorpus(
                keywordIndex,
                Map.of(
                        "chunk-a", new float[]{1.0f, 0.0f},
                        "chunk-b", new float[]{0.0f, 1.0f}),
                chunks,
                "test-model",
                "test-sha",
                2);
        return new RuntimeAnswerContent(
                "public-1", "hash-1", List.of(first, second), List.of(), corpus, List.of());
    }

    private AnswerKnowledge knowledge(
            String stableId, String slug, String claimId, String evidenceId) {
        AnswerClaimProjection claim = new AnswerClaimProjection(
                claimId,
                AnswerClaimCategory.IMPLEMENTATION,
                "公开实现说明",
                "公开实现细节",
                AnswerAchievementStatus.IMPLEMENTED_TESTED,
                AnswerContributionType.PRIMARY,
                AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED,
                AnswerMateriality.KEY,
                List.of("实现"),
                List.of(evidenceId));
        AnswerEvidence evidence = new AnswerEvidence(
                evidenceId,
                evidenceId,
                "公开证据",
                "DOCUMENT",
                LocalDate.of(2026, 1, 1),
                null,
                1,
                "公开摘要",
                "APPROVED",
                false);
        return new AnswerKnowledge(
                AnswerSubjectType.PROJECT,
                stableId,
                slug,
                stableId,
                "公开摘要",
                "背景",
                List.of("职责"),
                "方案",
                List.of("决策"),
                List.of("验证"),
                "结果",
                "交接",
                "完成",
                "BACKEND",
                Set.of("JAVA"),
                List.of(),
                List.of(evidence),
                List.of(claim));
    }

    private TurnDeadline activeDeadline() {
        return new TurnDeadline(Instant.parse("2026-08-18T00:01:00Z"), clock());
    }

    private Clock clock() {
        return Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC);
    }
}
