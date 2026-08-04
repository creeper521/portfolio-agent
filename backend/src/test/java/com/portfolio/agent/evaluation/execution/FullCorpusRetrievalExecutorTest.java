package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.answer.domain.EmbeddingVector;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import com.portfolio.agent.answer.gateway.LocalEmbeddingPort;
import com.portfolio.agent.answer.service.KeywordRetriever;
import com.portfolio.agent.answer.service.ReciprocalRankFusion;
import com.portfolio.agent.answer.service.RetrievalContextValidator;
import com.portfolio.agent.answer.service.RetrievalQueryNormalizer;
import com.portfolio.agent.answer.service.VectorRetriever;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalMessage;
import com.portfolio.agent.evaluation.domain.EvalObservation;
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
import com.portfolio.agent.portfolio.domain.RagDocument;
import com.portfolio.agent.portfolio.domain.RetrievalManifest;
import com.portfolio.agent.portfolio.domain.ReviewStatus;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.domain.RuntimeKeywordIndex;
import com.portfolio.agent.portfolio.domain.RuntimeRetrievalContent;
import com.portfolio.agent.portfolio.domain.RuntimeVectorIndex;
import com.portfolio.agent.portfolio.domain.SupportType;
import com.portfolio.agent.portfolio.domain.VerificationBasis;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FullCorpusRetrievalExecutorTest {

    @Test
    void retrievesAcrossTheWholeCorpusWithoutExpectedSubjectFilter() {
        RuntimeContentSnapshot snapshot = snapshot();
        LocalEmbeddingPort embeddingPort = query -> new EmbeddingVector(new float[]{1.0f, 0.0f});
        FullCorpusRetrievalExecutor executor = new FullCorpusRetrievalExecutor(
                snapshot, RetrievalPolicy.firstRelease(), new RetrievalQueryNormalizer(),
                new KeywordRetriever(), new VectorRetriever(), new ReciprocalRankFusion(),
                new RetrievalContextValidator(), embeddingPort);

        EvalObservation observation = executor.execute(
                new EvalExecutionInput(
                        "route.sql.001", List.of(new EvalMessage("user", "sql audit risk")),
                        EvalLayer.FULL_CORPUS_RETRIEVAL, 1),
                new EvalRunContext("run-001", snapshot.getContentVersion()));

        assertThat(observation.getSelectedProjectSlug()).isEqualTo("sql-audit-project");
        assertThat(observation.getSelectedChunkIds().getFirst()).isEqualTo("chunk-sql-audit");
    }

    private RuntimeContentSnapshot snapshot() {
        ProjectProfile sql = project("project-sql", "sql-audit-project", "claim-sql", "evidence-sql");
        ProjectProfile image = project("project-image", "image-audit-project", "claim-image", "evidence-image");
        PortfolioSnapshot source = new PortfolioSnapshot(
                "3.0", "2026-08-04.1", OffsetDateTime.parse("2026-08-04T12:00:00+08:00"),
                new OwnerProfile("", "Backend developer", "Portfolio", null, null, null),
                List.of(sql, image), List.of(), List.of(
                        claim("claim-sql", sql.getId()), claim("claim-image", image.getId())),
                List.of(link("claim-sql", "evidence-sql"), link("claim-image", "evidence-image")),
                List.of(), List.of(evidence("evidence-sql"), evidence("evidence-image")), List.of());
        return new RuntimeContentSnapshot(
                source, "sha256:full-corpus-test", Instant.parse("2026-08-04T04:00:00Z"),
                retrievalContent());
    }

    private RuntimeRetrievalContent retrievalContent() {
        RuntimeKeywordIndex keywordIndex = new RuntimeKeywordIndex(
                2, 2.0,
                List.of(document("chunk-image-audit", Map.of("image", 2, "audit", 1)),
                        document("chunk-sql-audit", Map.of("sql", 2, "audit", 1, "risk", 1))),
                Map.of("image", 1, "sql", 1, "audit", 2, "risk", 1));
        Map<String, float[]> vectors = new LinkedHashMap<String, float[]>();
        vectors.put("chunk-image-audit", new float[]{0.0f, 1.0f});
        vectors.put("chunk-sql-audit", new float[]{1.0f, 0.0f});
        RetrievalManifest manifest = new RetrievalManifest(
                "hybrid-rag-v1", "nfkc-bigram-v1", "retrieval-policy-v1",
                "BAAI/bge-small-zh-v1.5", "sha256:model", 2, 256, "L2", "COSINE", 2,
                "sha256:chunks", "keyword-index-v1", "vector-index-v1");
        return new RuntimeRetrievalContent(
                manifest,
                List.of(ragDocument("chunk-image-audit", "image-audit-project", "claim-image"),
                        ragDocument("chunk-sql-audit", "sql-audit-project", "claim-sql")),
                keywordIndex, new RuntimeVectorIndex(2, vectors));
    }

    private RuntimeKeywordIndex.DocumentEntry document(String chunkId, Map<String, Integer> terms) {
        int documentLength = terms.values().stream().mapToInt(Integer::intValue).sum();
        return new RuntimeKeywordIndex.DocumentEntry(chunkId, documentLength, terms);
    }

    private RagDocument ragDocument(String chunkId, String projectSlug, String claimId) {
        return new RagDocument(chunkId, "2026-08-04.1", List.of(projectSlug), List.of(),
                List.of(claimId), "Published retrieval content", List.of("RETRIEVAL"),
                LocalDate.parse("2026-08-01"), null, "sha256:" + chunkId);
    }

    private ProjectProfile project(String id, String slug, String claimId, String evidenceId) {
        return new ProjectProfile(id, id, slug, slug, "Summary", "Background",
                List.of("Responsibility"), "Solution", List.of("Decision"), List.of("Java"),
                List.of("Verified"), "Outcome", "Handoff", ProjectStatus.DELIVERED,
                ContributionType.PRIMARY, List.of(claimId), List.of(evidenceId), List.of());
    }

    private Claim claim(String id, String projectId) {
        return new Claim(id, ClaimSubjectType.PROJECT, projectId, ClaimCategory.OUTCOME,
                "Statement " + id, "Detail", AchievementStatus.DELIVERED,
                ContributionType.PRIMARY, VerificationBasis.EVIDENCE_SUPPORTED,
                ClaimVerificationStatus.VERIFIED, Materiality.KEY, List.of("RETRIEVAL"),
                Map.of("INTERVIEWER", 100));
    }

    private ClaimEvidenceLink link(String claimId, String evidenceId) {
        return new ClaimEvidenceLink("link-" + claimId, claimId, evidenceId,
                SupportType.DIRECT, "Supports " + claimId, ReviewStatus.APPROVED);
    }

    private EvidenceRecord evidence(String id) {
        return new EvidenceRecord(id, id, id, EvidenceType.DOCUMENT,
                LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-04"), 1,
                "Summary", EvidenceStatus.APPROVED, false);
    }
}
