package com.portfolio.agent.evaluation.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EvalCliTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void disableSpringAssembly() {
        System.setProperty("portfolio.eval.cli.spring", "false");
    }

    @AfterAll
    static void restoreSpringAssembly() {
        System.clearProperty("portfolio.eval.cli.spring");
    }

    @Test
    void validateWithPartialSubjectCoverageFails() throws Exception {
        Path manifest = writeSuite();
        Path policy = writePolicy();

        // a single case cannot cover all 58 public subjects of the real bundle:
        // validate must fail closed instead of silently passing
        int exit = EvalCli.run(new String[]{
                "validate",
                "--manifest", manifest.toString(),
                "--policy", policy.toString(),
                "--output-dir", tempDir.resolve("out-validate").toString(),
        });

        assertThat(exit).isEqualTo(EvalCli.EXIT_FAIL);
        String report = Files.readString(
                tempDir.resolve("out-validate/report.json"), StandardCharsets.UTF_8);
        assertThat(report).contains("\"verdict\":\"FAIL\"");
        assertThat(report).contains("\"content.smokeCoverage\"");
        assertThat(report).contains("\"dataset.caseCount\"");
    }

    @Test
    void offlineWithUnsupportedLayerExitsFail() throws Exception {
        Path manifest = writeSuite();
        Path policy = writePolicy();

        int exit = EvalCli.run(new String[]{
                "offline",
                "--manifest", manifest.toString(),
                "--policy", policy.toString(),
                "--output-dir", tempDir.resolve("out-offline").toString(),
        });

        assertThat(exit).isEqualTo(EvalCli.EXIT_FAIL);
        String report = Files.readString(
                tempDir.resolve("out-offline/report.json"), StandardCharsets.UTF_8);
        assertThat(report).contains("EXECUTOR_MISSING");
    }

    @Test
    void legacyCommandRunsTheLegacySuiteAsAnIndependentOfflineReport() throws Exception {
        Path manifest = writeSuite("legacy-suite");
        Path policy = writePolicy();

        int exit = EvalCli.run(new String[]{
                "legacy",
                "--manifest", manifest.toString(),
                "--policy", policy.toString(),
                "--output-dir", tempDir.resolve("out-legacy").toString(),
        });

        // legacy runs the same deterministic offline path with its own suite:
        // unsupported layers report EXECUTOR_MISSING and the report carries the
        // legacy suite id in its own output directory
        assertThat(exit).isEqualTo(EvalCli.EXIT_FAIL);
        String report = Files.readString(
                tempDir.resolve("out-legacy/report.json"), StandardCharsets.UTF_8);
        assertThat(report).contains("\"mode\":\"OFFLINE\"", "\"verdict\":\"FAIL\"");
        assertThat(report).contains("EXECUTOR_MISSING");
        assertThat(report).contains("\"datasetVersion\":\"2026-08-06.1\"");
    }

    @Test
    void unknownArgumentExitsInvalid() throws Exception {
        int exit = EvalCli.run(new String[]{
                "validate",
                "--unknown-flag", "x",
        });

        assertThat(exit).isEqualTo(EvalCli.EXIT_INVALID);
    }

    @Test
    void duplicateArgumentExitsInvalid() throws Exception {
        int exit = EvalCli.run(new String[]{
                "validate",
                "--manifest", "a",
                "--manifest", "b",
        });

        assertThat(exit).isEqualTo(EvalCli.EXIT_INVALID);
    }

    @Test
    void existingOutputDirectoryExitsInvalid() throws Exception {
        Path manifest = writeSuite();
        Path policy = writePolicy();
        Files.createDirectories(tempDir.resolve("exists"));

        int exit = EvalCli.run(new String[]{
                "validate",
                "--manifest", manifest.toString(),
                "--policy", policy.toString(),
                "--output-dir", tempDir.resolve("exists").toString(),
        });

        assertThat(exit).isEqualTo(EvalCli.EXIT_INVALID);
    }

    @Test
    void providerWithoutOfflineReportExitsInvalid() throws Exception {
        Path manifest = writeSuite();
        Path policy = writePolicy();

        int exit = EvalCli.run(new String[]{
                "provider",
                "--manifest", manifest.toString(),
                "--policy", policy.toString(),
                "--output-dir", tempDir.resolve("out-provider").toString(),
        });

        assertThat(exit).isEqualTo(EvalCli.EXIT_INVALID);
    }

    @Test
    void providerWithoutAuthorizationReportsIncomplete() throws Exception {
        Path manifest = writeSuite();
        Path policy = writePolicy();
        Path offline = writeOfflinePrerequisite(manifest, policy, "basic");

        int exit = EvalCli.run(new String[]{
                "provider",
                "--manifest", manifest.toString(),
                "--policy", policy.toString(),
                "--offline-report", offline.toString(),
                "--output-dir", tempDir.resolve("out-provider2").toString(),
        });

        assertThat(exit).isEqualTo(EvalCli.EXIT_INCOMPLETE);
        String report = Files.readString(
                tempDir.resolve("out-provider2/report.json"), StandardCharsets.UTF_8);
        assertThat(report).contains("\"verdict\":\"INCOMPLETE\"");
    }

    @Test
    void providerCommandRunsThreeMockTrialsWithoutRealProviderAuthorization()
            throws Exception {
        // HIGH risk + 3 trials makes the case provider-eligible; without
        // --authorize-real-provider the deterministic mock seam runs all three
        // trials and the report records providerInvoked instead of INCOMPLETE.
        Path casesDir = Files.createDirectories(tempDir.resolve("cases"));
        Path caseFile = casesDir.resolve("high-case.json");
        Files.writeString(caseFile, """
                {
                  "schemaVersion": "1.0",
                  "suiteId": "cli-test",
                  "datasetVersion": "2026-08-06.1",
                  "cases": [
                    {
                      "id": "provider-high-1",
                      "title": "高危主体评测",
                      "split": "HOLDOUT",
                      "origin": "HUMAN_AUTHORED",
                      "riskLevel": "HIGH",
                      "reviewStatus": "APPROVED",
                      "reviewerId": "cli",
                      "sourceCategory": "PUBLIC_BUNDLE",
                      "difficultyReason": "test",
                      "firstExposedDatasetVersion": "2026-08-06.1",
                      "tags": ["provider"],
                      "input": {"messages": [{"role": "user", "content": "高危评测问题"}]},
                      "oracle": {"expectedSubjects": [{"type": "CASE", "slug": "case-a"}]},
                      "expectations": {"resolution": ["ANSWERED"], "answerScope": ["PORTFOLIO"], "requiredClaimIds": [], "allowedEvidenceIds": [], "forbiddenSubjectSlugs": [], "forbiddenBehaviors": []},
                      "execution": {"layers": ["PROVIDER"], "providerTrials": 3},
                      "graders": [{"type": "SUBJECT_MATCH", "severity": "BLOCKING"}],
                      "maintenance": {"subjectRefs": [], "generatedFromBundle": false}
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);
        String manifest = """
                {
                  "schemaVersion": "1.0",
                  "suiteId": "cli-test",
                  "datasetVersion": "2026-08-06.1",
                  "trackedCaseFiles": ["cases/high-case.json"],
                  "generationRuleFiles": [],
                  "challenge": {"source": "EXTERNAL_ONLY", "pathStoredInRepository": false}
                }
                """;
        Path manifestPath = tempDir.resolve("suite-high.json");
        Files.writeString(manifestPath, manifest, StandardCharsets.UTF_8);
        Path policy = writePolicy();
        Path offline = writeOfflinePrerequisite(manifestPath, policy, "high");

        int exit = EvalCli.run(new String[]{
                "provider",
                "--manifest", manifestPath.toString(),
                "--policy", policy.toString(),
                "--offline-report", offline.toString(),
                "--output-dir", tempDir.resolve("out-provider-mock").toString(),
        });

        // mock answers carry no subject, so SUBJECT_MATCH fails and the run is
        // a real FAIL verdict — the pipeline ran, it did not fake a pass
        assertThat(exit).isEqualTo(EvalCli.EXIT_FAIL);
        String report = Files.readString(
                tempDir.resolve("out-provider-mock/report.json"), StandardCharsets.UTF_8);
        assertThat(report).contains("\"mode\":\"PROVIDER\"");
        assertThat(report).contains("\"verdict\":\"FAIL\"");
        assertThat(report).contains("\"providerInvoked\":true");
        assertThat(report).contains("\"trialIndex\":3");
        assertThat(report).doesNotContain("\"verdict\":\"INCOMPLETE\"");
    }

    @Test
    void realProviderAuthorizationWithoutAvailableBeanNeverFallsBackToMock()
            throws Exception {
        // Adversarial check: asking for --authorize-real-provider when no real
        // seam bean is available (tests run with the Spring assembly disabled)
        // must NOT silently fall back to the mock seam nor report a pass.
        Path manifest = writeSuite();
        Path policy = writePolicy();
        Path offline = writeOfflinePrerequisite(manifest, policy, "real");

        int exit = EvalCli.run(new String[]{
                "provider",
                "--manifest", manifest.toString(),
                "--policy", policy.toString(),
                "--offline-report", offline.toString(),
                "--authorize-real-provider",
                "--output-dir", tempDir.resolve("out-provider-real").toString(),
        });

        // STANDARD case is not provider-eligible, so no trial runs at all and
        // the report records the real-provider state as INCOMPLETE
        assertThat(exit).isEqualTo(EvalCli.EXIT_INCOMPLETE);
        String report = Files.readString(
                tempDir.resolve("out-provider-real/report.json"), StandardCharsets.UTF_8);
        assertThat(report).contains("\"providerRealState\":\"INCOMPLETE\"");
        assertThat(report).doesNotContain("\"providerInvoked\":true");
        assertThat(report).doesNotContain("\"verdict\":\"PASS\"");
    }

    private Path writeSuite() throws Exception {
        return writeSuite("cli-test");
    }

    private Path writeSuite(String suiteId) throws Exception {
        Path casesDir = Files.createDirectories(tempDir.resolve("cases"));
        Path caseFile = casesDir.resolve("cli-case.json");
        String caseJson = """
                {
                  "schemaVersion": "1.0",
                  "suiteId": "%s",
                  "datasetVersion": "2026-08-06.1",
                  "cases": [
                    {
                      "id": "cli-case-1",
                      "title": "SQL 审计与故障排查工具",
                      "split": "HOLDOUT",
                      "origin": "HUMAN_AUTHORED",
                      "riskLevel": "STANDARD",
                      "reviewStatus": "APPROVED",
                      "reviewerId": "cli",
                      "sourceCategory": "PUBLIC_BUNDLE",
                      "difficultyReason": "test",
                      "firstExposedDatasetVersion": "2026-08-06.1",
                      "tags": ["test"],
                      "input": {"messages": [{"role": "user", "content": "介绍 SQL 审计项目"}]},
                      "oracle": {"expectedSubjects": [{"type": "PROJECT", "slug": "sql-audit"}]},
                      "expectations": {"resolution": ["ANSWERED"], "answerScope": ["PORTFOLIO"], "requiredClaimIds": [], "allowedEvidenceIds": [], "forbiddenSubjectSlugs": [], "forbiddenBehaviors": []},
                      "execution": {"layers": ["INTELLIGENCE"], "providerTrials": 3},
                      "graders": [{"type": "SUBJECT_MATCH", "severity": "BLOCKING"}],
                      "maintenance": {"subjectRefs": [{"type": "PROJECT", "slug": "sql-audit"}], "generatedFromBundle": true}
                    }
                  ]
                }
                """;
        Files.writeString(caseFile, caseJson.formatted(suiteId), StandardCharsets.UTF_8);
        String manifest = """
                {
                  "schemaVersion": "1.0",
                  "suiteId": "%s",
                  "datasetVersion": "2026-08-06.1",
                  "trackedCaseFiles": ["cases/cli-case.json"],
                  "generationRuleFiles": [],
                  "challenge": {"source": "EXTERNAL_ONLY", "pathStoredInRepository": false}
                }
                """;
        Path manifestPath = tempDir.resolve("suite.json");
        Files.writeString(manifestPath, manifest.formatted(suiteId), StandardCharsets.UTF_8);
        return manifestPath;
    }

    @Test
    void validateWithEmptyDatasetFails() throws Exception {
        Path casesDir = Files.createDirectories(tempDir.resolve("cases"));
        Path emptyCaseFile = casesDir.resolve("empty-case.json");
        Files.writeString(emptyCaseFile, """
                {"schemaVersion": "1.0", "suiteId": "cli-test",
                 "datasetVersion": "2026-08-06.1", "cases": []}
                """, StandardCharsets.UTF_8);
        String manifest = """
                {
                  "schemaVersion": "1.0",
                  "suiteId": "cli-test",
                  "datasetVersion": "2026-08-06.1",
                  "trackedCaseFiles": ["cases/empty-case.json"],
                  "generationRuleFiles": [],
                  "challenge": {"source": "EXTERNAL_ONLY", "pathStoredInRepository": false}
                }
                """;
        Path manifestPath = tempDir.resolve("suite-empty.json");
        Files.writeString(manifestPath, manifest, StandardCharsets.UTF_8);
        Path policy = writePolicy();

        int exit = EvalCli.run(new String[]{
                "validate",
                "--manifest", manifestPath.toString(),
                "--policy", policy.toString(),
                "--output-dir", tempDir.resolve("out-empty").toString(),
        });

        assertThat(exit).isEqualTo(EvalCli.EXIT_FAIL);
        String report = Files.readString(
                tempDir.resolve("out-empty/report.json"), StandardCharsets.UTF_8);
        assertThat(report).contains("\"verdict\":\"FAIL\"");
        assertThat(report).contains("\"dataset.caseCount\"");
    }

    @Test
    void validateExpandsSmokeCasesFromGenerationRulesIntoTheReport() throws Exception {
        Path manifest = writeSuite();
        Path policy = writePolicy();
        // track a generation rule (copied beside the manifest, relative path)
        Path casesDir = Files.createDirectories(tempDir.resolve("cases"));
        Path rulesDir = Files.createDirectories(casesDir.resolve("generation-rules"));
        Path sourceRule = Path.of("..", "governance", "portfolio-governance",
                "evaluation", "generation-rules", "public-subject-smoke.v1.json");
        Files.copy(sourceRule, rulesDir.resolve("public-subject-smoke.v1.json"));
        String manifestWithRule = """
                {
                  "schemaVersion": "1.0",
                  "suiteId": "cli-test",
                  "datasetVersion": "2026-08-06.1",
                  "trackedCaseFiles": ["cases/cli-case.json"],
                  "generationRuleFiles": ["cases/generation-rules/public-subject-smoke.v1.json"],
                  "challenge": {"source": "EXTERNAL_ONLY", "pathStoredInRepository": false}
                }
                """;
        Path manifestPath = tempDir.resolve("suite-with-rule.json");
        Files.writeString(manifestPath, manifestWithRule, StandardCharsets.UTF_8);

        int exit = EvalCli.run(new String[]{
                "validate",
                "--manifest", manifestPath.toString(),
                "--policy", policy.toString(),
                "--output-dir", tempDir.resolve("out-expanded").toString(),
        });

        assertThat(exit).isEqualTo(EvalCli.EXIT_PASS);
        String report = Files.readString(
                tempDir.resolve("out-expanded/report.json"), StandardCharsets.UTF_8);
        assertThat(report).contains("\"expandedCases\"");
        assertThat(report).contains("\"smoke.project.sql-audit\"");
        assertThat(report).contains("\"smoke.case.multilingual-image-preservation\"");
    }

    @Test
    void datasetHashChangesWhenCaseContentChanges() throws Exception {
        Path manifest = writeSuite();
        Path policy = writePolicy();

        int firstExit = EvalCli.run(new String[]{
                "validate",
                "--manifest", manifest.toString(),
                "--policy", policy.toString(),
                "--output-dir", tempDir.resolve("out-hash-1").toString(),
        });
        String firstReport = Files.readString(
                tempDir.resolve("out-hash-1/report.json"), StandardCharsets.UTF_8);
        String firstHash = firstReport.replaceFirst(
                ".*\"datasetHash\":\"([0-9a-f]{64})\".*", "$1");

        // change the handwritten case content and re-validate
        Path caseFile = tempDir.resolve("cases/cli-case.json");
        String original = Files.readString(caseFile, StandardCharsets.UTF_8);
        Files.writeString(caseFile,
                original.replace("cli-case-1", "cli-case-1-modified"),
                StandardCharsets.UTF_8);
        int secondExit = EvalCli.run(new String[]{
                "validate",
                "--manifest", manifest.toString(),
                "--policy", policy.toString(),
                "--output-dir", tempDir.resolve("out-hash-2").toString(),
        });
        String secondReport = Files.readString(
                tempDir.resolve("out-hash-2/report.json"), StandardCharsets.UTF_8);
        String secondHash = secondReport.replaceFirst(
                ".*\"datasetHash\":\"([0-9a-f]{64})\".*", "$1");

        assertThat(firstExit).isEqualTo(EvalCli.EXIT_FAIL);
        assertThat(secondExit).isEqualTo(EvalCli.EXIT_FAIL);
        assertThat(firstHash).hasSize(64);
        assertThat(secondHash).hasSize(64);
        assertThat(secondHash).isNotEqualTo(firstHash);
    }

    private Path writePolicy() throws Exception {
        String policy = """
                {
                  "policyId": "phase-0.v1",
                  "mode": "OFFLINE",
                  "blockingProvider": "DEEPSEEK_V4_FLASH",
                  "thresholds": {
                    "blocking": {
                      "publicSubjectSmokeCoverageMinimum": 1.0,
                      "namedRouteTopOneMinimum": 1.0,
                      "deepSemanticRouteTopOneMinimum": 0.9,
                      "priorityDeepSemanticRouteTopOneMinimum": 0.95,
                      "retrievalHitAtFiveMinimum": 0.9,
                      "requiredClaimRecallMinimum": 0.9,
                      "providerTrialPassRateMinimum": 0.9,
                      "providerScenarioPassRateMinimum": 0.9,
                      "safetyBoundaryPassRateMinimum": 1.0,
                      "falseSufficientMaximum": 0.0,
                      "providerFailureRateMaximum": 0.02,
                      "providerP95LatencyMaximumMs": 20000,
                      "priorityMetricRegressionMaximum": 0.02,
                      "globalMetricRegressionMaximum": 0.03
                    },
                    "scored": {
                      "answerQualityPassRateMinimum": 0.8
                    }
                  },
                  "trialPolicy": {
                    "defaultTrials": 3,
                    "standardMinimumPasses": 2,
                    "highMinimumPasses": 3,
                    "invariantMinimumPasses": 3
                  },
                  "pricing": {
                    "currency": "CNY",
                    "budget": 5.0
                  }
                }
                """;
        Path policyPath = tempDir.resolve("policy.json");
        Files.writeString(policyPath, policy, StandardCharsets.UTF_8);
        return policyPath;
    }

    private Path writeOfflinePrerequisite(
            Path manifest,
            Path policy,
            String suffix) throws Exception {
        Path validateOutput = tempDir.resolve("offline-prerequisite-" + suffix);
        int validateExit = EvalCli.run(new String[]{
                "validate",
                "--manifest", manifest.toString(),
                "--policy", policy.toString(),
                "--output-dir", validateOutput.toString(),
        });
        // The identity is still authoritative even when this small fixture's
        // validate gates fail for coverage; provider tests only need a
        // structurally valid PASS prerequisite with matching identity.
        String validateJson = Files.readString(
                validateOutput.resolve("report.json"), StandardCharsets.UTF_8);
        ObjectNode source = (ObjectNode) new ObjectMapper().readTree(validateJson);
        ObjectNode prerequisite = new ObjectMapper().createObjectNode();
        prerequisite.put("mode", "OFFLINE");
        prerequisite.put("verdict", "PASS");
        prerequisite.set("identity", source.get("identity"));
        Path offline = tempDir.resolve("offline-report-" + suffix + ".json");
        Files.writeString(offline,
                new ObjectMapper().writeValueAsString(prerequisite),
                StandardCharsets.UTF_8);
        return offline;
    }
}
