package com.portfolio.agent.evaluation.dataset;

import com.portfolio.agent.evaluation.dataset.GenerationRuleLoader.GenerationRule;
import com.portfolio.agent.evaluation.domain.EvalCase;
import com.portfolio.agent.evaluation.domain.EvalGraderRule;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalRiskLevel;
import com.portfolio.agent.evaluation.domain.EvalSuite;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PhaseZeroDatasetAcceptanceTest {

    private static final Path GOVERNANCE =
            Path.of("../governance/portfolio-governance/evaluation");

    @Test
    void phaseZeroCoreManifestLoadsWithStableIdsAndCompleteCoverage() throws Exception {
        EvalManifestLoader loader = new EvalManifestLoader();
        EvalManifestLoader.EvalManifest manifest = loader.load(
                GOVERNANCE.resolve("manifest.v1.json"));

        assertThat(manifest.getDatasetVersion()).isEqualTo("2026-08-06.1");
        assertThat(manifest.getTrackedCaseFiles()).hasSize(4);
        assertThat(manifest.getGenerationRuleFiles()).hasSize(1);

        EvalSuiteLoader suiteLoader = new EvalSuiteLoader(
                new com.fasterxml.jackson.databind.ObjectMapper());
        List<EvalCase> cases = new java.util.ArrayList<>();
        for (Path file : manifest.getTrackedCaseFiles()) {
            cases.addAll(suiteLoader.load(Files.readAllBytes(file)).getCases());
        }
        assertThat(cases).isNotEmpty();
        // smoke cases are no longer handwritten: the generator expands them at runtime
        assertThat(cases.stream()
                .filter(caseItem -> caseItem.getId().startsWith("smoke.")))
                .isEmpty();

        Set<String> ids = new HashSet<>();
        for (EvalCase evalCase : cases) {
            assertThat(ids.add(evalCase.getId()))
                    .as("case id %s must be unique", evalCase.getId()).isTrue();
        }

        assertThat(cases.stream()
                .filter(caseItem -> caseItem.getId().startsWith("answer.sql-audit.")))
                .hasSize(12);
        assertThat(cases.stream()
                .filter(caseItem -> caseItem.getId().startsWith("safety.")))
                .hasSize(1);
        assertThat(cases.stream()
                .filter(caseItem -> caseItem.getId().startsWith("semantic-turn.")))
                .singleElement()
                .satisfies(caseItem -> assertThat(caseItem.getTags())
                        .contains("phase-2-semantic-turn", "multi-task", "dependency",
                                "mixed-source", "exclusion"));

        for (EvalCase evalCase : cases) {
            if (evalCase.getId().startsWith("answer.sql-audit.")) {
                assertThat(evalCase.getLayers())
                        .contains(EvalLayer.HTTP_E2E, EvalLayer.HTTP_E2E);
            }
            for (EvalGraderRule rule : evalCase.getGraders()) {
                assertThat(knownGraderTypes()).contains(rule.getType());
            }
        }
        for (EvalCase evalCase : cases) {
            if (evalCase.getId().startsWith("routing.")
                    || evalCase.getId().startsWith("answer.")) {
                assertThat(evalCase.getProviderTrials()).isEqualTo(3);
            }
        }
    }

    @Test
    void smokeCasesAreExpandedDeterministicallyFromTheGenerationRule() throws Exception {
        EvalManifestLoader loader = new EvalManifestLoader();
        EvalManifestLoader.EvalManifest manifest = loader.load(
                GOVERNANCE.resolve("manifest.v1.json"));
        GenerationRuleLoader ruleLoader = new GenerationRuleLoader();
        GenerationRule rule = ruleLoader.load(
                GOVERNANCE.resolve(manifest.getGenerationRuleFiles().get(0)));
        RuntimeContentSnapshot bundle = bundledSnapshot();

        SmokeCaseGenerator generator = new SmokeCaseGenerator();
        List<EvalCase> expanded = generator.generate(bundle, rule);
        List<EvalCase> expandedAgain = generator.generate(bundle, rule);

        assertThat(expanded).hasSize(bundle.getProjects().size() + bundle.getCases().size());
        assertThat(expanded).extracting(EvalCase::getId)
                .containsExactly(expandedAgain.stream()
                        .map(EvalCase::getId).toArray(String[]::new));
        assertThat(expanded).allSatisfy(evalCase ->
                assertThat(evalCase.getId()).matches("smoke\\.(project|case)\\.[a-z0-9-]+"));
        assertThat(expanded).allSatisfy(evalCase ->
                assertThat(evalCase.getLayers()).contains(EvalLayer.BUNDLE_CONTRACT));
        // no handwritten case may collide with a generated id
        List<EvalCase> handwritten = new java.util.ArrayList<>();
        for (Path file : manifest.getTrackedCaseFiles()) {
            handwritten.addAll(new EvalSuiteLoader(
                    new com.fasterxml.jackson.databind.ObjectMapper())
                    .load(Files.readAllBytes(file)).getCases());
        }
        Set<String> handwrittenIds = new HashSet<>();
        for (EvalCase evalCase : handwritten) {
            handwrittenIds.add(evalCase.getId());
        }
        assertThat(expanded).allSatisfy(evalCase ->
                assertThat(handwrittenIds).doesNotContain(evalCase.getId()));
    }

    @Test
    void legacyDatasetArtifactsAreRetired() {
        assertThat(GOVERNANCE.resolve("manifest.legacy.v1.json")).doesNotExist();
        assertThat(GOVERNANCE.resolve("cases/regression/legacy.v1.json")).doesNotExist();
    }

    @Test
    void policyFileMatchesTheLoaderContract() throws Exception {
        EvalPolicyLoader loader = new EvalPolicyLoader();
        com.portfolio.agent.evaluation.domain.EvalPolicy policy = loader.load(
                GOVERNANCE.resolve("policies/phase-0.v1.json"));

        assertThat(policy.getPolicyId()).isEqualTo("phase-0.v1");
        assertThat(policy.getPublicSubjectSmokeCoverageMinimum())
                .isEqualByComparingTo("1.0");
        assertThat(policy.getProviderP95LatencyMaximumMs()).isEqualTo(20_000L);
        assertThat(policy.getDefaultTrials()).isEqualTo(3);
    }

    private RuntimeContentSnapshot bundledSnapshot() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        com.portfolio.agent.portfolio.repository.file.PublicBundleLoader loader =
                new com.portfolio.agent.portfolio.repository.file.PublicBundleLoader(
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

    private byte[] readResource(String path) throws Exception {
        try (java.io.InputStream stream = PhaseZeroDatasetAcceptanceTest.class.getClassLoader()
                .getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("missing classpath resource: " + path);
            }
            return stream.readAllBytes();
        }
    }

    private Set<String> knownGraderTypes() {
        return Set.of("SUBJECT_MATCH", "REFERENCE_INTEGRITY", "RESOLUTION",
                "ANSWER_SCOPE", "REQUIRED_CLAIMS", "GROUNDING",
                "FORBIDDEN_SUBJECT", "API_CONTRACT", "ANSWER_QUALITY");
    }
}
