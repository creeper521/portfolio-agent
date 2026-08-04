package com.portfolio.agent.evaluation.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.EmbeddingVector;
import com.portfolio.agent.answer.domain.RetrievalDecisionType;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import com.portfolio.agent.answer.service.KeywordRetriever;
import com.portfolio.agent.answer.service.ReciprocalRankFusion;
import com.portfolio.agent.answer.service.RetrievalContextValidator;
import com.portfolio.agent.answer.service.RetrievalQueryNormalizer;
import com.portfolio.agent.answer.service.VectorRetriever;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.RagDocument;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.repository.file.PublicBundleLoader;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import com.portfolio.agent.release.benchmark.RetrievalBenchmarkCase;
import com.portfolio.agent.release.benchmark.RetrievalBenchmarkCategory;
import com.portfolio.agent.release.benchmark.RetrievalBenchmarkSplit;
import com.portfolio.agent.release.benchmark.RetrievalBenchmarkSuite;
import com.portfolio.agent.release.benchmark.RetrievalComparisonRunner;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyRetrievalBenchmarkAdapterTest {

    @Test
    void labelsExistingSubjectFilteredBenchmarkAsSubjectInternalDiagnostic() throws Exception {
        RuntimeContentSnapshot snapshot = loadRuntimeBundle();
        ProjectProfile project = snapshot.getProjects().getFirst();
        RagDocument chunk = retrievalChunkFor(snapshot, project.getSlug());
        RetrievalBenchmarkSuite suite = new RetrievalBenchmarkSuite(
                "legacy-test", snapshot.getContentVersion(), List.of(new RetrievalBenchmarkCase(
                        "legacy.route.001", RetrievalBenchmarkSplit.CALIBRATION,
                        RetrievalBenchmarkCategory.EXACT_TERM, ClaimSubjectType.PROJECT,
                        project.getSlug(), project.getTitle(), List.of(chunk.getClaimIds().getFirst()),
                        List.of(chunk.getChunkId()), RetrievalDecisionType.SUFFICIENT)));
        float[] vector = snapshot.getRetrievalContent().orElseThrow().getVectorIndex()
                .getVectors().values().iterator().next();
        RetrievalComparisonRunner runner = new RetrievalComparisonRunner(
                new RetrievalQueryNormalizer(), new KeywordRetriever(), new VectorRetriever(),
                new ReciprocalRankFusion(), new RetrievalContextValidator(),
                query -> new EmbeddingVector(vector));
        LegacyRetrievalBenchmarkAdapter adapter = new LegacyRetrievalBenchmarkAdapter(runner);

        assertThat(adapter.run(suite, snapshot, RetrievalPolicy.currentRelease()))
                .allSatisfy(observation -> assertThat(observation.getLayer())
                        .isEqualTo(EvalLayer.SUBJECT_INTERNAL_RETRIEVAL));
        assertThat((Object) adapter).isNotInstanceOf(EvalExecutor.class);
    }

    private RagDocument retrievalChunkFor(RuntimeContentSnapshot snapshot, String projectSlug) {
        return snapshot.getRetrievalContent().orElseThrow().getDocuments().stream()
                .filter(document -> document.getProjectSlugs().contains(projectSlug))
                .filter(document -> !document.getClaimIds().isEmpty())
                .findFirst()
                .orElseThrow();
    }

    private RuntimeContentSnapshot loadRuntimeBundle() throws Exception {
        Set<String> names = Set.of("manifest.json", "portfolio.json", "presentation.json",
                "rag-documents.jsonl", "keyword-index.json", "vector-index.bin", "checksums.json");
        Map<String, byte[]> files = new HashMap<String, byte[]>();
        for (String name : names) {
            files.put(name, resource("/public-data/bundle/" + name));
        }
        return new PublicBundleLoader(new ObjectMapper().findAndRegisterModules(),
                new PortfolioSnapshotValidator(), Clock.systemUTC()).load(files);
    }

    private byte[] resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing test resource: " + path);
            }
            return input.readAllBytes();
        }
    }
}
