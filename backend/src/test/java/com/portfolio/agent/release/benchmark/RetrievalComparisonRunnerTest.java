package com.portfolio.agent.release.benchmark;

import com.portfolio.agent.answer.domain.AnswerKeywordIndex;
import com.portfolio.agent.answer.domain.AnswerRetrievalChunk;
import com.portfolio.agent.answer.domain.AnswerRetrievalCorpus;
import com.portfolio.agent.answer.domain.EmbeddingVector;
import com.portfolio.agent.answer.domain.RetrievalDecisionType;
import com.portfolio.agent.answer.domain.RetrievalMode;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import com.portfolio.agent.answer.gateway.LocalEmbeddingPort;
import com.portfolio.agent.answer.service.KeywordRetriever;
import com.portfolio.agent.answer.service.RankedRetrievalHit;
import com.portfolio.agent.answer.service.ReciprocalRankFusion;
import com.portfolio.agent.answer.service.RetrievalContextValidator;
import com.portfolio.agent.answer.service.RetrievalQueryNormalizer;
import com.portfolio.agent.answer.service.VectorRetriever;
import com.portfolio.agent.portfolio.domain.AchievementStatus;
import com.portfolio.agent.portfolio.domain.Claim;
import com.portfolio.agent.portfolio.domain.ClaimCategory;
import com.portfolio.agent.portfolio.domain.ClaimEvidenceLink;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;
import com.portfolio.agent.portfolio.domain.ClaimVerificationStatus;
import com.portfolio.agent.portfolio.domain.ContributionType;
import com.portfolio.agent.portfolio.domain.EvidenceRecord;
import com.portfolio.agent.portfolio.domain.EvidenceStatus;
import com.portfolio.agent.portfolio.domain.EvidenceType;
import com.portfolio.agent.portfolio.domain.Materiality;
import com.portfolio.agent.portfolio.domain.OwnerProfile;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.ProjectStatus;
import com.portfolio.agent.portfolio.domain.ReviewStatus;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.domain.SupportType;
import com.portfolio.agent.portfolio.domain.VerificationBasis;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RetrievalComparisonRunnerTest {

    @Test
    void evaluatesThreeRoutesWithOneNormalizedEmbeddingAndSubjectFilteringPerCase() {
        RetrievalBenchmarkSuite suite = suite();
        RuntimeContentSnapshot snapshot = snapshot();
        AnswerRetrievalCorpus corpus = corpus();
        RetrievalPolicy policy = RetrievalPolicy.firstRelease();
        AtomicInteger embeddingCalls = new AtomicInteger();
        LocalEmbeddingPort embeddingPort = query -> {
            embeddingCalls.incrementAndGet();
            return query.contains("sql")
                    ? new EmbeddingVector(new float[]{1.0f, 0.0f})
                    : new EmbeddingVector(new float[]{0.0f, 1.0f});
        };
        RetrievalQueryNormalizer normalizer = spy(new RetrievalQueryNormalizer());
        ReciprocalRankFusion fusion = spy(new ReciprocalRankFusion());
        RetrievalContextValidator validator = spy(new RetrievalContextValidator());
        RetrievalComparisonRunner runner = new RetrievalComparisonRunner(
                normalizer,
                new KeywordRetriever(),
                new VectorRetriever(),
                fusion,
                validator,
                embeddingPort
        );

        List<RetrievalRouteEvaluation> evaluations =
                runner.run(suite, snapshot, corpus, policy);

        assertThat(embeddingCalls.get()).isEqualTo(suite.getCases().size());
        verify(normalizer, times(suite.getCases().size())).normalize(any());
        assertThat(evaluations).hasSize(suite.getCases().size() * 3);
        assertThat(evaluations).extracting(RetrievalRouteEvaluation::getRoute)
                .contains(RetrievalBenchmarkRoute.KEYWORD,
                        RetrievalBenchmarkRoute.VECTOR,
                        RetrievalBenchmarkRoute.HYBRID);
        assertThat(evaluations).allSatisfy(evaluation -> {
            assertThat(evaluation.getActualDecision())
                    .isEqualTo(RetrievalDecisionType.SUFFICIENT);
            assertThat(evaluation.getExpectedRank()).isEqualTo(1);
            assertThat(evaluation.getSelectedChunkIds())
                    .doesNotContain("aaa-distractor");
        });

        ArgumentCaptor<List<RankedRetrievalHit>> keywordInputs =
                listCaptor();
        ArgumentCaptor<List<RankedRetrievalHit>> vectorInputs =
                listCaptor();
        verify(fusion, times(suite.getCases().size() * 3)).fuse(
                keywordInputs.capture(), vectorInputs.capture(), eq(policy.getRrfK()));
        for (int caseIndex = 0; caseIndex < suite.getCases().size(); caseIndex++) {
            int firstCall = caseIndex * 3;
            assertThat(vectorInputs.getAllValues().get(firstCall)).isEmpty();
            assertThat(keywordInputs.getAllValues().get(firstCall + 1)).isEmpty();
            assertThat(keywordInputs.getAllValues().get(firstCall))
                    .isSameAs(keywordInputs.getAllValues().get(firstCall + 2));
            assertThat(vectorInputs.getAllValues().get(firstCall + 1))
                    .isSameAs(vectorInputs.getAllValues().get(firstCall + 2));
        }
        assertThat(keywordInputs.getAllValues())
                .allSatisfy(hits -> assertThat(hits)
                        .extracting(RankedRetrievalHit::getChunkId)
                        .doesNotContain("aaa-distractor"));
        assertThat(vectorInputs.getAllValues())
                .allSatisfy(hits -> assertThat(hits)
                        .extracting(RankedRetrievalHit::getChunkId)
                        .doesNotContain("aaa-distractor"));

        ArgumentCaptor<RetrievalMode> modes = ArgumentCaptor.forClass(RetrievalMode.class);
        verify(validator, times(suite.getCases().size() * 3)).validate(
                anyList(), anyList(), anyMap(), anyList(), modes.capture(), same(policy));
        assertThat(modes.getAllValues()).containsExactly(
                RetrievalMode.KEYWORD_ONLY,
                RetrievalMode.HYBRID_ENABLED,
                RetrievalMode.HYBRID_ENABLED,
                RetrievalMode.KEYWORD_ONLY,
                RetrievalMode.HYBRID_ENABLED,
                RetrievalMode.HYBRID_ENABLED
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<List<RankedRetrievalHit>> listCaptor() {
        return ArgumentCaptor.forClass((Class) List.class);
    }

    private RetrievalBenchmarkSuite suite() {
        return new RetrievalBenchmarkSuite(
                "retrieval-benchmark-v2",
                "2026-07-23.1",
                List.of(
                        benchmarkCase(
                                "exact-sql",
                                RetrievalBenchmarkCategory.EXACT_TERM,
                                "SQL",
                                "claim-sql",
                                "chunk-sql"
                        ),
                        benchmarkCase(
                                "semantic-agent-mcp",
                                RetrievalBenchmarkCategory.SEMANTIC_PARAPHRASE,
                                "Agent MCP",
                                "claim-agent",
                                "chunk-agent"
                        )
                )
        );
    }

    private RetrievalBenchmarkCase benchmarkCase(
            String caseId,
            RetrievalBenchmarkCategory category,
            String query,
            String expectedClaimId,
            String expectedChunkId
    ) {
        return new RetrievalBenchmarkCase(
                caseId,
                RetrievalBenchmarkSplit.CALIBRATION,
                category,
                ClaimSubjectType.PROJECT,
                "target-project",
                query,
                List.of(expectedClaimId),
                List.of(expectedChunkId),
                RetrievalDecisionType.SUFFICIENT
        );
    }

    private AnswerRetrievalCorpus corpus() {
        AnswerKeywordIndex keywordIndex = new AnswerKeywordIndex(
                3,
                2.0,
                List.of(
                        document("aaa-distractor", Map.of("sql", 1, "agent", 1, "mcp", 1)),
                        document("chunk-agent", Map.of("agent", 1, "mcp", 1)),
                        document("chunk-sql", Map.of("sql", 2))
                ),
                Map.of("sql", 2, "agent", 2, "mcp", 2)
        );
        Map<String, float[]> vectors = new LinkedHashMap<>();
        vectors.put("aaa-distractor", new float[]{1.0f, 1.0f});
        vectors.put("chunk-agent", new float[]{0.0f, 1.0f});
        vectors.put("chunk-sql", new float[]{1.0f, 0.0f});
        Map<String, AnswerRetrievalChunk> chunks = new LinkedHashMap<>();
        chunks.put("aaa-distractor", chunk(
                "aaa-distractor", "other-project", "claim-other"));
        chunks.put("chunk-agent", chunk(
                "chunk-agent", "target-project", "claim-agent"));
        chunks.put("chunk-sql", chunk(
                "chunk-sql", "target-project", "claim-sql"));
        return new AnswerRetrievalCorpus(keywordIndex, vectors, chunks);
    }

    private AnswerKeywordIndex.DocumentEntry document(
            String chunkId,
            Map<String, Integer> terms
    ) {
        int documentLength = terms.values().stream().mapToInt(Integer::intValue).sum();
        return new AnswerKeywordIndex.DocumentEntry(chunkId, documentLength, terms);
    }

    private AnswerRetrievalChunk chunk(
            String chunkId,
            String projectSlug,
            String claimId
    ) {
        return new AnswerRetrievalChunk(
                chunkId,
                List.of(projectSlug),
                List.of(claimId),
                List.of("RETRIEVAL"),
                120
        );
    }

    private RuntimeContentSnapshot snapshot() {
        ProjectProfile target = project(
                "project-target",
                "target-project",
                List.of("claim-sql", "claim-agent"),
                List.of("evidence-sql", "evidence-agent")
        );
        ProjectProfile other = project(
                "project-other",
                "other-project",
                List.of("claim-other"),
                List.of("evidence-other")
        );
        PortfolioSnapshot source = new PortfolioSnapshot(
                "3.0",
                "2026-07-23.1",
                OffsetDateTime.parse("2026-07-23T12:00:00+08:00"),
                new OwnerProfile("", "Backend developer", "Portfolio", null, null, null),
                List.of(target, other),
                List.of(),
                List.of(
                        claim("claim-sql", target.getId()),
                        claim("claim-agent", target.getId()),
                        claim("claim-other", other.getId())
                ),
                List.of(
                        link("link-sql", "claim-sql", "evidence-sql"),
                        link("link-agent", "claim-agent", "evidence-agent"),
                        link("link-other", "claim-other", "evidence-other")
                ),
                List.of(),
                List.of(
                        evidence("evidence-sql"),
                        evidence("evidence-agent"),
                        evidence("evidence-other")
                ),
                List.of()
        );
        return new RuntimeContentSnapshot(
                source,
                "sha256:test-runtime-bundle",
                Instant.parse("2026-07-23T04:00:00Z")
        );
    }

    private ProjectProfile project(
            String id,
            String slug,
            List<String> claimIds,
            List<String> evidenceIds
    ) {
        return new ProjectProfile(
                id,
                id,
                slug,
                slug,
                "Summary",
                "Background",
                List.of("Responsibility"),
                "Solution",
                List.of("Decision"),
                List.of("Java"),
                List.of("Verified"),
                "Outcome",
                "Handoff",
                ProjectStatus.DELIVERED,
                ContributionType.PRIMARY,
                claimIds,
                evidenceIds,
                List.of()
        );
    }

    private Claim claim(String id, String projectId) {
        return new Claim(
                id,
                ClaimSubjectType.PROJECT,
                projectId,
                ClaimCategory.OUTCOME,
                "Statement " + id,
                "Detail",
                AchievementStatus.DELIVERED,
                ContributionType.PRIMARY,
                VerificationBasis.EVIDENCE_SUPPORTED,
                ClaimVerificationStatus.VERIFIED,
                Materiality.KEY,
                List.of("RETRIEVAL"),
                Map.of("INTERVIEWER", 100)
        );
    }

    private ClaimEvidenceLink link(String id, String claimId, String evidenceId) {
        return new ClaimEvidenceLink(
                id,
                claimId,
                evidenceId,
                SupportType.DIRECT,
                "Supports " + claimId,
                ReviewStatus.APPROVED
        );
    }

    private EvidenceRecord evidence(String id) {
        return new EvidenceRecord(
                id,
                id,
                id,
                EvidenceType.DOCUMENT,
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-20"),
                1,
                "Summary",
                EvidenceStatus.APPROVED,
                false
        );
    }
}
