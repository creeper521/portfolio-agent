package com.portfolio.agent.evaluation.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.evaluation.dataset.GenerationRuleLoader.GenerationRule;
import com.portfolio.agent.evaluation.domain.EvalCase;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.repository.file.PublicBundleLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmokeCaseGeneratorTest {

    private final SmokeCaseGenerator generator = new SmokeCaseGenerator();

    private final GenerationRule rule = new GenerationRule(
            "1.0", "public-subject-smoke", "PUBLIC",
            List.of("PROJECT", "CASE"), "PUBLIC_SUBJECT_SMOKE_V1");

    @Test
    void everyPublicSubjectInTheBundledSnapshotYieldsExactlyOneCase() {
        RuntimeContentSnapshot bundle = bundledSnapshot();

        List<EvalCase> cases = generator.generate(bundle, rule);

        int projects = bundle.getProjects().size();
        int caseStudies = bundle.getCases().size();
        assertThat(cases).hasSize(projects + caseStudies);
        assertThat(cases).allSatisfy(evalCase ->
                assertThat(evalCase.getId()).startsWith("smoke."));
    }

    @Test
    void generationIsDeterministicAndSorted() {
        RuntimeContentSnapshot bundle = bundledSnapshot();

        List<EvalCase> first = generator.generate(bundle, rule);
        List<EvalCase> second = generator.generate(bundle, rule);

        assertThat(first).extracting(EvalCase::getId)
                .containsExactlyElementsOf(second.stream()
                        .map(EvalCase::getId).toList());
        assertThat(first).extracting(EvalCase::getId)
                .isSorted();
        assertThat(first).extracting(EvalCase::getTitle)
                .contains("SQL 审计与故障排查工具");
    }

    @Test
    void generatedCaseCarriesBundleContractLayerAndBundleGeneratedOrigin() {
        List<EvalCase> cases = generator.generate(bundledSnapshot(), rule);

        EvalCase smoke = cases.get(0);
        assertThat(smoke.getLayers())
                .containsExactly(com.portfolio.agent.evaluation.domain.EvalLayer.BUNDLE_CONTRACT);
        assertThat(smoke.getOrigin())
                .isEqualTo(com.portfolio.agent.evaluation.domain.EvalOrigin.BUNDLE_GENERATED);
        assertThat(smoke.getProviderTrials()).isEqualTo(3);
        assertThat(smoke.getFirstExposedDatasetVersion())
                .isEqualTo(bundledSnapshot().getContentVersion());
    }

    @Test
    void rejectsNonPublicSelector() {
        GenerationRule invalid = new GenerationRule(
                "1.0", "x", "PRIVATE", List.of("PROJECT", "CASE"), "PUBLIC_SUBJECT_SMOKE_V1");

        assertThatThrownBy(() -> generator.generate(bundledSnapshot(), invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnsupportedTemplate() {
        GenerationRule invalid = new GenerationRule(
                "1.0", "x", "PUBLIC", List.of("PROJECT", "CASE"), "OTHER_TEMPLATE");

        assertThatThrownBy(() -> generator.generate(bundledSnapshot(), invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private RuntimeContentSnapshot bundledSnapshot() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        PublicBundleLoader loader = new PublicBundleLoader(
                mapper,
                new com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator(),
                java.time.Clock.systemUTC());
        java.util.Map<String, byte[]> files = new java.util.LinkedHashMap<>();
        for (String name : List.of(
                "manifest.json", "portfolio.json", "presentation.json",
                "rag-documents.jsonl", "keyword-index.json",
                "vector-index.bin", "checksums.json")) {
            files.put(name, readResource("public-data/bundle/" + name));
        }
        return loader.load(files);
    }

    private byte[] readResource(String path) {
        try (InputStream stream = SmokeCaseGeneratorTest.class.getClassLoader()
                .getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("missing classpath resource: " + path);
            }
            return stream.readAllBytes();
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "unable to read classpath resource: " + path, failure);
        }
    }
}
