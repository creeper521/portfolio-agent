package com.portfolio.agent.release.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.RetrievalDecisionType;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class WaveOneRetrievalBenchmarkCoverageTest {

    private static final Set<String> WAVE_ZERO_CASE_IDS = Set.of(
            "case-codegraph-workflow-paraphrase-01",
            "case-multilingual-preservation-paraphrase-01",
            "case-role-reset-flow-paraphrase-01",
            "negative-injection-01",
            "negative-privacy-contact-01",
            "negative-revenue-forecast-01",
            "negative-weather-01",
            "sql-background-exact-01",
            "sql-delivered-exact-01",
            "sql-responsibility-exact-01",
            "sql-routing-decision-exact-01",
            "sql-verification-exact-01"
    );

    private static final Set<String> WAVE_ONE_KEY_CLAIM_IDS = Set.of(
            "claim-sql-audit-background",
            "claim-sql-audit-responsibility",
            "claim-sql-audit-technical-decision",
            "claim-sql-audit-verification",
            "claim-sql-audit-delivered",
            "claim-sql-audit-fixed-string-search",
            "claim-sql-audit-source-selection",
            "claim-sql-audit-partial-success",
            "claim-case-multilingual-preserve-existing",
            "claim-case-multilingual-sequential-verification",
            "claim-case-role-reset-controlled-flow",
            "claim-case-role-reset-acceptance",
            "claim-case-codegraph-narrowing",
            "claim-case-codegraph-failure-boundary",
            "claim-case-codegraph-combined-workflow",
            "claim-sql-audit-async-task-lifecycle",
            "claim-sql-audit-progress-fallback",
            "claim-sql-audit-result-lifecycle",
            "claim-sql-audit-truncation-disclosure",
            "claim-case-multilingual-replacement-problem",
            "claim-case-role-reset-cache-interference-problem",
            "claim-case-role-reset-confirmation-safety",
            "claim-case-codegraph-evaluation-method",
            "claim-case-codegraph-manual-quality-review"
    );

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void freezesWaveOneRetrievalCasesAndHoldoutCoverage() throws Exception {
        RetrievalBenchmarkSuite suite = loadRetrievalSuite();

        assertThat(suite.getSuiteVersion()).isEqualTo("retrieval-benchmark-v3-wave1");
        assertThat(suite.getContentVersion()).isEqualTo("2026-07-24.1");
        assertThat(suite.getCases()).hasSize(34);
        assertThat(caseIds(suite)).containsAll(WAVE_ZERO_CASE_IDS);

        Set<String> holdoutClaimIds = suite.getCases().stream()
                .filter(item -> item.getSplit() == RetrievalBenchmarkSplit.HOLDOUT)
                .flatMap(item -> item.getExpectedClaimIds().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertThat(holdoutClaimIds).containsAll(WAVE_ONE_KEY_CLAIM_IDS);

        Map<String, Long> naturalQuestionsBySubject = suite.getCases().stream()
                .filter(item -> item.getExpectedDecision() == RetrievalDecisionType.SUFFICIENT)
                .collect(Collectors.groupingBy(
                        item -> item.getSubjectType().name() + ":" + item.getSubjectSlug(),
                        LinkedHashMap::new,
                        Collectors.counting()));
        assertThat(naturalQuestionsBySubject).containsOnlyKeys(
                "PROJECT:sql-audit",
                "CASE:multilingual-image-preservation",
                "CASE:test-role-reset",
                "CASE:codegraph-evaluation");
        assertThat(naturalQuestionsBySubject.values()).allMatch(count -> count >= 3);

        assertNegative(suite, "negative-sql-failed-source-retry-01",
                ClaimSubjectType.PROJECT, "sql-audit");
        assertNegative(suite, "negative-multilingual-historical-backfill-01",
                ClaimSubjectType.CASE, "multilingual-image-preservation");
        assertNegative(suite, "negative-role-reset-arbitrary-batch-delete-01",
                ClaimSubjectType.CASE, "test-role-reset");
        assertNegative(suite, "negative-codegraph-universal-productivity-01",
                ClaimSubjectType.CASE, "codegraph-evaluation");

        RetrievalBenchmarkCase manualQualityReview = caseById(
                suite, "case-codegraph-manual-quality-review-paraphrase-01");
        assertThat(manualQualityReview.getQuery())
                .isEqualTo("自动评测显示任务成功后，为什么仍要人工复核答案质量？")
                .doesNotContainIgnoringCase("token")
                .doesNotContain("压缩");

        Set<String> calibrationQueries = queriesForSplit(
                suite, RetrievalBenchmarkSplit.CALIBRATION);
        Set<String> holdoutQueries = queriesForSplit(suite, RetrievalBenchmarkSplit.HOLDOUT);
        assertThat(calibrationQueries).doesNotContainAnyElementsOf(holdoutQueries);

        Set<String> calibrationIntents = intentSignaturesForSplit(
                suite, RetrievalBenchmarkSplit.CALIBRATION);
        Set<String> holdoutIntents = intentSignaturesForSplit(
                suite, RetrievalBenchmarkSplit.HOLDOUT);
        assertThat(calibrationIntents).doesNotContainAnyElementsOf(holdoutIntents);
    }

    @Test
    void freezesWaveOneGovernancePresetCoverage() throws Exception {
        JsonNode benchmark = mapper.readTree(Files.readAllBytes(projectRoot().resolve(
                "governance/portfolio-governance/benchmark/active-benchmarks.v1.json")));
        Map<String, Set<String>> caseTypesByPreset = new LinkedHashMap<>();
        Map<String, Set<String>> claimIdsByPreset = new LinkedHashMap<>();
        for (JsonNode item : benchmark.path("cases")) {
            String presetId = item.path("questionPresetId").asText();
            caseTypesByPreset.computeIfAbsent(presetId, ignored -> new TreeSet<>())
                    .add(item.path("caseType").asText());
            if ("CLAIM_EVIDENCE".equals(item.path("caseType").asText())) {
                Set<String> claimIds = claimIdsByPreset.computeIfAbsent(
                        presetId, ignored -> new TreeSet<>());
                for (JsonNode claimId : item.path("requiredClaimIds")) {
                    claimIds.add(claimId.asText());
                }
            }
        }

        Set<String> requiredCaseTypes = Set.of(
                "SUPPORTED_QUESTION", "ALIAS", "BOUNDARY", "CLAIM_EVIDENCE", "SAFETY");
        Map<String, Set<String>> requiredClaimsByPreset = Map.of(
                "question-sql-audit-async-and-recovery",
                Set.of("claim-sql-audit-async-task-lifecycle"),
                "question-sql-audit-progress-fallback",
                Set.of("claim-sql-audit-progress-fallback"),
                "question-sql-audit-archive-and-truncation",
                Set.of("claim-sql-audit-result-lifecycle",
                        "claim-sql-audit-truncation-disclosure"),
                "question-case-multilingual-verification-sequence",
                Set.of("claim-case-multilingual-sequential-verification"),
                "question-case-multilingual-recovery-boundary",
                Set.of("claim-case-multilingual-replacement-problem"),
                "question-case-role-reset-acceptance-result",
                Set.of("claim-case-role-reset-cache-interference-problem",
                        "claim-case-role-reset-acceptance"),
                "question-case-role-reset-safety-boundary",
                Set.of("claim-case-role-reset-confirmation-safety"),
                "question-case-codegraph-method",
                Set.of("claim-case-codegraph-evaluation-method"),
                "question-case-codegraph-quality-boundary",
                Set.of("claim-case-codegraph-manual-quality-review")
        );

        for (Map.Entry<String, Set<String>> expected : requiredClaimsByPreset.entrySet()) {
            assertThat(caseTypesByPreset.get(expected.getKey()))
                    .as("governance case types for %s", expected.getKey())
                    .containsExactlyInAnyOrderElementsOf(requiredCaseTypes);
            assertThat(claimIdsByPreset.get(expected.getKey()))
                    .as("governance KEY Claim coverage for %s", expected.getKey())
                    .containsAll(expected.getValue());
        }
    }

    private RetrievalBenchmarkSuite loadRetrievalSuite() throws Exception {
        Path cases = projectRoot().resolve(
                "backend/src/test/resources/retrieval-benchmark/cases.json");
        return new RetrievalBenchmarkCaseLoader(mapper).load(Files.readAllBytes(cases));
    }

    private Set<String> caseIds(RetrievalBenchmarkSuite suite) {
        return suite.getCases().stream()
                .map(RetrievalBenchmarkCase::getCaseId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void assertNegative(
            RetrievalBenchmarkSuite suite,
            String caseId,
            ClaimSubjectType subjectType,
            String subjectSlug
    ) {
        RetrievalBenchmarkCase item = caseById(suite, caseId);
        assertThat(item.getSplit()).isEqualTo(RetrievalBenchmarkSplit.HOLDOUT);
        assertThat(item.getSubjectType()).isEqualTo(subjectType);
        assertThat(item.getSubjectSlug()).isEqualTo(subjectSlug);
        assertThat(item.getExpectedClaimIds()).isEmpty();
        assertThat(item.getExpectedChunkIds()).isEmpty();
        assertThat(item.getExpectedDecision()).isEqualTo(RetrievalDecisionType.AMBIGUOUS);
    }

    private RetrievalBenchmarkCase caseById(
            RetrievalBenchmarkSuite suite,
            String caseId
    ) {
        List<RetrievalBenchmarkCase> matches = suite.getCases().stream()
                .filter(item -> item.getCaseId().equals(caseId))
                .toList();
        assertThat(matches).hasSize(1);
        return matches.getFirst();
    }

    private Set<String> queriesForSplit(
            RetrievalBenchmarkSuite suite,
            RetrievalBenchmarkSplit split
    ) {
        return suite.getCases().stream()
                .filter(item -> item.getSplit() == split)
                .map(item -> item.getQuery().strip().toLowerCase())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> intentSignaturesForSplit(
            RetrievalBenchmarkSuite suite,
            RetrievalBenchmarkSplit split
    ) {
        return suite.getCases().stream()
                .filter(item -> item.getSplit() == split)
                .map(this::intentSignature)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String intentSignature(RetrievalBenchmarkCase item) {
        String subject = item.getSubjectType().name() + "|" + item.getSubjectSlug();
        if (item.getExpectedDecision() == RetrievalDecisionType.SUFFICIENT) {
            // Positive exact-term and paraphrase cases are the same intent when
            // their subject and expected Claims are identical.
            return subject + "|" + String.join(",", new TreeSet<>(item.getExpectedClaimIds()))
                    + "|" + item.getExpectedDecision().name();
        }
        // Empty-Claim safety cases use category as the explicit negative intent:
        // INJECTION, PRIVACY and UNSUPPORTED_OR_WITHDRAWN are distinct boundaries.
        return subject + "|" + item.getCategory().name()
                + "|" + item.getExpectedDecision().name();
    }

    private Path projectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return Files.isDirectory(current.resolve("backend")) ? current : current.getParent();
    }
}
