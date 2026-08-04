package com.portfolio.agent.evaluation.coverage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.evaluation.domain.EvalCase;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.repository.file.PublicBundleLoader;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EvalSmokeCaseGeneratorTest {

    @Test
    void generatesOneStableSmokeCasePerPublicProjectAndCase() throws Exception {
        RuntimeContentSnapshot snapshot = loadRuntimeBundle();

        List<EvalCase> generated = new EvalSmokeCaseGenerator().generate(snapshot);

        assertThat(generated).hasSize(58);
        assertThat(generated).extracting(EvalCase::getId)
                .contains("smoke.project.sql-audit", "smoke.case.test-role-reset")
                .isSorted();
        assertThat(generated).allMatch(EvalCase::isGeneratedFromBundle);
        assertThat(generated).allSatisfy(item -> {
            assertThat(item.getInputMessages()).hasSize(1);
            assertThat(item.getLayers()).containsExactly(
                    EvalLayer.BUNDLE_CONTRACT,
                    EvalLayer.FULL_CORPUS_RETRIEVAL,
                    EvalLayer.INTELLIGENCE);
            assertThat(item.getProviderTrials()).isEqualTo(1);
        });
    }

    private RuntimeContentSnapshot loadRuntimeBundle() throws Exception {
        Set<String> names = Set.of(
                "manifest.json", "portfolio.json", "presentation.json", "rag-documents.jsonl",
                "keyword-index.json", "vector-index.bin", "checksums.json");
        Map<String, byte[]> files = new HashMap<String, byte[]>();
        for (String name : names) {
            files.put(name, resource("/public-data/bundle/" + name));
        }
        return new PublicBundleLoader(
                new ObjectMapper().findAndRegisterModules(),
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
