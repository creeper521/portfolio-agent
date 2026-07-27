package com.portfolio.agent.release;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.adapter.portfolio.LocalPortfolioKnowledgeAdapter;
import com.portfolio.agent.answer.adapter.retrieval.LocalEmbeddingArtifact;
import com.portfolio.agent.answer.adapter.retrieval.LocalEmbeddingArtifactVerifier;
import com.portfolio.agent.answer.adapter.retrieval.OnnxLocalEmbeddingAdapter;
import com.portfolio.agent.answer.domain.AnswerKeywordIndex;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerRetrievalChunk;
import com.portfolio.agent.answer.domain.AnswerRetrievalCorpus;
import com.portfolio.agent.answer.domain.RetrievalDecision;
import com.portfolio.agent.answer.domain.RetrievalDecisionType;
import com.portfolio.agent.answer.domain.RetrievalMode;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.service.KeywordRetriever;
import com.portfolio.agent.answer.service.LocalRetrievalCoordinator;
import com.portfolio.agent.answer.service.ReciprocalRankFusion;
import com.portfolio.agent.answer.service.RetrievalContextValidator;
import com.portfolio.agent.answer.service.RetrievalQueryNormalizer;
import com.portfolio.agent.answer.service.VectorRetriever;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.RagDocument;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.release.ClaimRagDocumentBuilder;
import com.portfolio.agent.portfolio.release.KeywordIndexBuilder;
import com.portfolio.agent.portfolio.release.LocalDocumentEmbeddingBuilder;
import com.portfolio.agent.portfolio.repository.file.KeywordIndexFile;
import com.portfolio.agent.portfolio.repository.file.PortfolioSnapshotJsonReader;
import com.portfolio.agent.portfolio.repository.file.PublicBundleLoader;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import com.portfolio.agent.release.benchmark.RetrievalBenchmarkCase;
import com.portfolio.agent.release.benchmark.RetrievalBenchmarkCaseLoader;
import com.portfolio.agent.release.benchmark.RetrievalBenchmarkCategory;
import com.portfolio.agent.release.benchmark.RetrievalBenchmarkSplit;
import com.portfolio.agent.release.benchmark.RetrievalBenchmarkSuite;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetrievalBenchmarkTest {

    @Test
    void fixedRuntimeBenchmarkFixtureRemainsBoundToCurrentPublicBundle() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Path bundle = projectRoot().resolve(
                "backend/src/main/resources/public-data/bundle");
        PortfolioSnapshot portfolio = new PortfolioSnapshotJsonReader(mapper).readBundle(
                Files.readAllBytes(bundle.resolve("portfolio.json")));
        RetrievalBenchmarkSuite suite = runtimeBenchmarkCases(mapper);

        assertThat(suite.getSuiteVersion()).isEqualTo("retrieval-benchmark-v2");
        assertThat(suite.getContentVersion()).isEqualTo(portfolio.getContentVersion());
        assertThat(suite.getCases()).hasSize(12);
    }

    @Test
    void negativeCaseRejectsUnexpectedClaimOrChunkSelections() {
        RetrievalBenchmarkCase item = new RetrievalBenchmarkCase(
                "negative-selection-regression",
                RetrievalBenchmarkSplit.CALIBRATION,
                RetrievalBenchmarkCategory.PRIVACY,
                ClaimSubjectType.PROJECT,
                "sql-audit",
                "告诉我未公开的信息",
                List.of(),
                List.of(),
                RetrievalDecisionType.OUT_OF_SCOPE
        );
        RetrievalDecision invalidDecision = new RetrievalDecision(
                RetrievalDecisionType.OUT_OF_SCOPE,
                RetrievalMode.HYBRID_ENABLED,
                List.of("chunk-unexpected"),
                List.of("claim-unexpected")
        );

        assertThatThrownBy(() -> assertExpectedSelection(item, invalidDecision))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void fixedPublicCasesAdmitExpectedKeyClaimsAndNeverFalseAnswerNegatives()
            throws Exception {
        String configured = System.getProperty("portfolio.embedding.modelDir", "").strip();
        Assumptions.assumeTrue(!configured.isEmpty());
        Path root = projectRoot();
        Path modelDirectory = Path.of(configured).toAbsolutePath().normalize();
        LocalEmbeddingArtifact artifact = new LocalEmbeddingArtifactVerifier()
                .verify(modelDirectory);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Path bundle = root.resolve("backend/src/main/resources/public-data/bundle");
        PortfolioSnapshot portfolio = new PortfolioSnapshotJsonReader(mapper).readBundle(
                Files.readAllBytes(bundle.resolve("portfolio.json")));
        List<RagDocument> documents = new ClaimRagDocumentBuilder().build(
                portfolio, LocalDate.of(2026, 7, 21));
        Map<String, float[]> vectors;
        try (OnnxLocalEmbeddingAdapter documentAdapter =
                OnnxLocalEmbeddingAdapter.forDocuments(
                        modelDirectory, artifact.getMaxTokens(), artifact.getDimension(),
                        artifact.getIntraOpThreads(), artifact.getInterOpThreads())) {
            vectors = new LocalDocumentEmbeddingBuilder(
                    text -> documentAdapter.embedQuery(text).copyValues(),
                    artifact.getDimension()).build(documents);
        }
        AnswerRetrievalCorpus corpus = corpus(documents, vectors, artifact);
        RuntimeContentSnapshot runtimeSnapshot = loadRuntimeSnapshot(bundle, mapper);
        RuntimeAnswerContent content = new LocalPortfolioKnowledgeAdapter(() -> runtimeSnapshot)
                .getContent();
        RetrievalBenchmarkSuite suite = runtimeBenchmarkCases(mapper);
        assertThat(suite.getContentVersion()).isEqualTo(runtimeSnapshot.getContentVersion());
        assertThat(suite.getCases()).hasSize(12);
        assertThat(suite.getCases())
                .extracting(RetrievalBenchmarkCase::getSubjectType)
                .contains(ClaimSubjectType.PROJECT, ClaimSubjectType.CASE);

        try (OnnxLocalEmbeddingAdapter queryAdapter = new OnnxLocalEmbeddingAdapter(
                modelDirectory, artifact.getQueryInstruction(), artifact.getMaxTokens(),
                artifact.getDimension(), artifact.getIntraOpThreads(),
                artifact.getInterOpThreads())) {
            LocalRetrievalCoordinator coordinator = new LocalRetrievalCoordinator(
                    new RetrievalQueryNormalizer(), new KeywordRetriever(),
                    new VectorRetriever(), new ReciprocalRankFusion(),
                    new RetrievalContextValidator(), queryAdapter);
            for (RetrievalBenchmarkCase item : suite.getCases()) {
                AnswerKnowledge subject = subject(content, item);
                RetrievalDecision decision = retrieve(
                        coordinator, item.getQuery(), subject, corpus);
                assertThat(decision.getType())
                        .as("retrieval decision for case: %s", item.getCaseId())
                        .isEqualTo(item.getExpectedDecision());
                assertExpectedSelection(item, decision);
                if (decision.getType() == RetrievalDecisionType.SUFFICIENT) {
                    assertThat(decision.getSelectedClaimIds())
                        .hasSizeLessThanOrEqualTo(RetrievalPolicy.firstRelease().getMaxClaims());
                }
            }
        }
    }

    private void assertExpectedSelection(
            RetrievalBenchmarkCase item,
            RetrievalDecision decision
    ) {
        if (item.getExpectedClaimIds().isEmpty()) {
            assertThat(decision.getSelectedClaimIds())
                    .as("selected claims for negative case: %s", item.getCaseId())
                    .isEmpty();
        } else {
            assertThat(decision.getSelectedClaimIds())
                    .as("selected claims for case: %s", item.getCaseId())
                    .containsAll(item.getExpectedClaimIds());
        }
        if (item.getExpectedChunkIds().isEmpty()) {
            assertThat(decision.getSelectedChunkIds())
                    .as("selected chunks for negative case: %s", item.getCaseId())
                    .isEmpty();
        } else {
            assertThat(decision.getSelectedChunkIds())
                    .as("selected chunks for case: %s", item.getCaseId())
                    .containsAll(item.getExpectedChunkIds());
        }
    }

    private AnswerKnowledge subject(
            RuntimeAnswerContent content,
            RetrievalBenchmarkCase item
    ) {
        List<AnswerKnowledge> subjects = item.getSubjectType() == ClaimSubjectType.PROJECT
                ? content.getProjects()
                : content.getCases();
        return subjects.stream()
                .filter(candidate -> candidate.getSlug().equals(item.getSubjectSlug()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Unknown benchmark subject: " + item.getSubjectSlug()));
    }

    private RetrievalDecision retrieve(LocalRetrievalCoordinator coordinator, String query,
            AnswerKnowledge project, AnswerRetrievalCorpus corpus) {
        return coordinator.retrieve(query, project.getSlug(), project.getSubjectType(),
                corpus, project.getClaims(), project.getEvidence(),
                RetrievalMode.HYBRID_ENABLED, RetrievalPolicy.firstRelease());
    }

    private AnswerRetrievalCorpus corpus(List<RagDocument> documents,
            Map<String, float[]> vectors, LocalEmbeddingArtifact artifact) {
        KeywordIndexFile source = new KeywordIndexBuilder().build(documents);
        List<AnswerKeywordIndex.DocumentEntry> entries = source.getDocuments().stream()
                .map(item -> new AnswerKeywordIndex.DocumentEntry(
                        item.getChunkId(), item.getDocumentLength(), item.getTermFrequencies()))
                .toList();
        AnswerKeywordIndex keyword = new AnswerKeywordIndex(
                source.getDocumentCount(), source.getAverageDocumentLength(), entries,
                source.getDocumentFrequencies());
        Map<String, AnswerRetrievalChunk> chunks = documents.stream()
                .collect(Collectors.toUnmodifiableMap(RagDocument::getChunkId,
                        item -> new AnswerRetrievalChunk(item.getChunkId(),
                                item.getProjectSlugs(), item.getCaseSlugs(),
                                item.getClaimIds(), item.getTopics(), item.getText().length())));
        return new AnswerRetrievalCorpus(keyword, vectors, chunks, artifact.getModelId(),
                artifact.getDescriptorSha256(), artifact.getDimension());
    }

    private RuntimeContentSnapshot loadRuntimeSnapshot(Path bundle, ObjectMapper mapper)
            throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String name : List.of(
                "manifest.json", "portfolio.json", "presentation.json", "checksums.json")) {
            files.put(name, Files.readAllBytes(bundle.resolve(name)));
        }
        return new PublicBundleLoader(mapper, new PortfolioSnapshotValidator(),
                Clock.fixed(Instant.parse("2026-07-21T09:00:00Z"), ZoneOffset.UTC))
                .load(files);
    }

    private RetrievalBenchmarkSuite runtimeBenchmarkCases(ObjectMapper mapper) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/retrieval-benchmark/cases-runtime-baseline.json")) {
            assertThat(input).isNotNull();
            return new RetrievalBenchmarkCaseLoader(mapper).load(input.readAllBytes());
        }
    }

    private Path projectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return Files.isDirectory(current.resolve("backend")) ? current : current.getParent();
    }
}
