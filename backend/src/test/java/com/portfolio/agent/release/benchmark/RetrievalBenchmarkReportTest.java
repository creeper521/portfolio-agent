package com.portfolio.agent.release.benchmark;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.portfolio.agent.answer.domain.RetrievalDecisionType;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalBenchmarkReportTest {

    private final ObjectMapper canonicalMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    @Test
    void serializesIdenticallyWhenInputCollectionsAreReversed() throws Exception {
        RetrievalBenchmarkReport first = report(evaluations(), metrics());

        List<RetrievalRouteEvaluation> reversedEvaluations = new ArrayList<>(evaluations());
        Collections.reverse(reversedEvaluations);
        Map<RetrievalBenchmarkRoute, RetrievalBenchmarkMetrics> reversedMetrics = new LinkedHashMap<>();
        reversedMetrics.put(RetrievalBenchmarkRoute.HYBRID, metrics().get(RetrievalBenchmarkRoute.HYBRID));
        reversedMetrics.put(RetrievalBenchmarkRoute.VECTOR, metrics().get(RetrievalBenchmarkRoute.VECTOR));
        reversedMetrics.put(RetrievalBenchmarkRoute.KEYWORD, metrics().get(RetrievalBenchmarkRoute.KEYWORD));
        RetrievalBenchmarkReport second = report(reversedEvaluations, reversedMetrics);

        assertThat(canonicalMapper.writeValueAsBytes(first))
                .isEqualTo(canonicalMapper.writeValueAsBytes(second));
        assertThat(first.getEvaluations())
                .extracting(RetrievalBenchmarkReport.Evaluation::getRoute,
                        RetrievalBenchmarkReport.Evaluation::getCaseId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(RetrievalBenchmarkRoute.KEYWORD, "case-a"),
                        org.assertj.core.groups.Tuple.tuple(RetrievalBenchmarkRoute.KEYWORD, "case-b"),
                        org.assertj.core.groups.Tuple.tuple(RetrievalBenchmarkRoute.VECTOR, "case-a"),
                        org.assertj.core.groups.Tuple.tuple(RetrievalBenchmarkRoute.VECTOR, "case-c"),
                        org.assertj.core.groups.Tuple.tuple(RetrievalBenchmarkRoute.HYBRID, "case-b")
                );
    }

    @Test
    void retainsOnlySafeEvaluationFieldsAndFullPrecisionMetrics() throws Exception {
        RetrievalBenchmarkReport report = report(evaluations(), metrics());
        String json = canonicalMapper.writeValueAsString(report);

        assertThat(report.getMetricsByRoute().get(RetrievalBenchmarkRoute.HYBRID).getMrrAt5())
                .isEqualTo(1.0 / 3.0);
        JsonNode root = canonicalMapper.readTree(json);
        assertThat(root.path("runtimeBundleHash").asText())
                .isEqualTo("bundle-sha256");
        assertThat(root.path("snapshotValidFrom").asText())
                .isEqualTo("2026-07-23");
        assertThat(root.path("metricsByRoute").path("HYBRID")
                .path("positiveDecisionSuccessCount").asInt())
                .isEqualTo(1);
        assertThat(root.path("metricsByRoute").path("HYBRID")
                .path("positiveDecisionSuccessRate").asDouble())
                .isEqualTo(1.0);
        assertThat(root.findValues("timestamp")).isEmpty();
        assertThat(root.findValues("path")).isEmpty();
        assertThat(root.findValues("hostname")).isEmpty();
        assertThat(root.findValues("evidence")).isEmpty();
        assertThat(root.findValues("vector")).isEmpty();
        assertThat(root.findValues("score")).isEmpty();
        assertThat(root.findValues("selectedClaimIds")).isEmpty();
        assertThat(root.findValues("selectedChunkIds")).isEmpty();
    }

    @Test
    void serializesSubjectsExpectedItemRanksGroupedMetricsAndSafeRunMetadata()
            throws Exception {
        RetrievalBenchmarkRunMetadata metadata = new RetrievalBenchmarkRunMetadata(
                "17.0.12",
                "OpenJDK Runtime Environment",
                "Eclipse Adoptium",
                "Windows 11",
                "10.0",
                "amd64",
                8,
                Instant.parse("2026-07-24T01:02:03Z"),
                Instant.parse("2026-07-24T01:02:04Z"),
                321L,
                "retrieval-benchmark-v2",
                "2026-07-23.1",
                "bundle-sha256",
                "policy-v1",
                "BAAI/bge-small-zh-v1.5",
                "model-sha256",
                512
        );
        RetrievalRouteEvaluation evaluation = new RetrievalRouteEvaluation(
                RetrievalBenchmarkRoute.HYBRID,
                "case-ranks",
                RetrievalBenchmarkSplit.HOLDOUT,
                RetrievalBenchmarkCategory.SEMANTIC_PARAPHRASE,
                ClaimSubjectType.PROJECT,
                "sql-audit",
                RetrievalDecisionType.SUFFICIENT,
                RetrievalDecisionType.SUFFICIENT,
                2,
                List.of(
                        new RetrievalExpectedRank("CLAIM", "claim-z", null),
                        new RetrievalExpectedRank("CLAIM", "claim-a", 3)
                ),
                List.of(
                        new RetrievalExpectedRank("CHUNK", "chunk-z", 2),
                        new RetrievalExpectedRank("CHUNK", "chunk-a", null)
                ),
                List.of(),
                List.of()
        );
        RetrievalBenchmarkReport report = report(
                List.of(evaluation),
                new RetrievalBenchmarkEvaluator().evaluate(List.of(evaluation)),
                metadata
        );

        JsonNode root = canonicalMapper.readTree(
                canonicalMapper.writeValueAsBytes(report));
        JsonNode serialized = root.path("evaluations").get(0);
        assertThat(serialized.path("subjectType").asText()).isEqualTo("PROJECT");
        assertThat(serialized.path("subjectSlug").asText()).isEqualTo("sql-audit");
        assertThat(serialized.path("expectedRank").asInt()).isEqualTo(2);
        assertThat(serialized.path("expectedClaimRanks").toString())
                .isEqualTo("[{\"rank\":3,\"targetId\":\"claim-a\",\"targetType\":\"CLAIM\"},"
                        + "{\"rank\":null,\"targetId\":\"claim-z\",\"targetType\":\"CLAIM\"}]");
        assertThat(serialized.path("expectedChunkRanks").toString())
                .isEqualTo("[{\"rank\":null,\"targetId\":\"chunk-a\",\"targetType\":\"CHUNK\"},"
                        + "{\"rank\":2,\"targetId\":\"chunk-z\",\"targetType\":\"CHUNK\"}]");
        assertThat(root.path("splitRouteMetrics")).hasSize(1);
        assertThat(root.path("splitCategoryRouteMetrics")).hasSize(1);
        assertThat(root.path("splitSubjectRouteMetrics")).hasSize(1);
        assertThat(root.path("splitRouteDecisionCounts")).hasSize(1);
        assertThat(root.path("runMetadata").path("durationMillis").asLong())
                .isEqualTo(321L);
        assertThat(root.toString()).doesNotContain(
                "hostname", "query", "path", "vector", "rawScore",
                "credential", "secret");
    }

    @Test
    void derivesNullCompatibilityRankWhenEveryExpectedItemMisses() {
        RetrievalRouteEvaluation evaluation = new RetrievalRouteEvaluation(
                RetrievalBenchmarkRoute.VECTOR,
                "all-miss",
                RetrievalBenchmarkSplit.HOLDOUT,
                RetrievalBenchmarkCategory.SEMANTIC_PARAPHRASE,
                ClaimSubjectType.PROJECT,
                "sql-audit",
                RetrievalDecisionType.SUFFICIENT,
                RetrievalDecisionType.AMBIGUOUS,
                1,
                List.of(new RetrievalExpectedRank(
                        "CLAIM", "claim-missing", null)),
                List.of(new RetrievalExpectedRank(
                        "CHUNK", "chunk-missing", null)),
                List.of(),
                List.of()
        );

        assertThat(evaluation.getExpectedRank()).isNull();
    }

    @Test
    void rendersNeutralStableMarkdownWithMetricsCategoriesAndFailures() {
        RetrievalBenchmarkReport report = report(evaluations(), metrics());

        String markdown = new RetrievalBenchmarkMarkdownRenderer().render(report);

        assertThat(markdown).startsWith("# Retrieval Baseline Comparison\n");
        assertThat(markdown).contains(
                "- Suite version: `retrieval-benchmark-v2`",
                "- Content version: `2026-07-23.1`",
                "- Verified runtime Bundle hash: `bundle-sha256`",
                "- Snapshot validFrom: `2026-07-23`",
                "- Policy version: `policy-v1`",
                "- Model descriptor hash: `model-sha256`",
                "| Route | Positive cases | Hit@1 | Hit@5 | MRR@5 | Positive decision success | Positive decision success rate | False sufficient |",
                "| KEYWORD | 1 | 1.0000 | 1.0000 | 1.0000 | 1 | 1.0000 | 1 |",
                "| Category | Route | Positive cases | Hit@1 | Hit@5 | MRR@5 | Positive decision success | Positive decision success rate | False sufficient |",
                "| EXACT_TERM | KEYWORD | 1 | 1.0000 | 1.0000 | 1.0000 | 1 | 1.0000 | 1 |",
                "- KEYWORD: `case-b`",
                "- VECTOR: `case-a`",
                "- VECTOR: `case-c`");
        assertThat(markdown).doesNotContainIgnoringCase("hybrid is valuable", "hybrid provides value");
    }

    @Test
    void rendersHoldoutBeforeSeparateCalibrationWithoutMixedRouteConclusion() {
        List<RetrievalRouteEvaluation> mixed = List.of(
                evaluation(
                        RetrievalBenchmarkRoute.HYBRID,
                        "calibration",
                        RetrievalBenchmarkSplit.CALIBRATION,
                        RetrievalBenchmarkCategory.EXACT_TERM,
                        "calibration-project",
                        RetrievalDecisionType.SUFFICIENT,
                        RetrievalDecisionType.SUFFICIENT,
                        1),
                evaluation(
                        RetrievalBenchmarkRoute.HYBRID,
                        "holdout",
                        RetrievalBenchmarkSplit.HOLDOUT,
                        RetrievalBenchmarkCategory.SEMANTIC_PARAPHRASE,
                        "holdout-project",
                        RetrievalDecisionType.SUFFICIENT,
                        RetrievalDecisionType.AMBIGUOUS,
                        null)
        );
        RetrievalBenchmarkReport report = report(
                mixed,
                new RetrievalBenchmarkEvaluator().evaluate(mixed));

        String markdown = new RetrievalBenchmarkMarkdownRenderer().render(report);

        assertThat(markdown.indexOf("## Holdout route metrics"))
                .isGreaterThanOrEqualTo(0)
                .isLessThan(markdown.indexOf("## Calibration route metrics"));
        String holdoutSection = markdown.substring(
                markdown.indexOf("## Holdout route metrics"),
                markdown.indexOf("## Calibration route metrics"));
        assertThat(holdoutSection)
                .contains("| HYBRID | 1 | 0.0000 | 0.0000 | 0.0000")
                .doesNotContain("calibration-project");
    }

    private RetrievalBenchmarkReport report(
            List<RetrievalRouteEvaluation> evaluations,
            Map<RetrievalBenchmarkRoute, RetrievalBenchmarkMetrics> metrics
    ) {
        return new RetrievalBenchmarkReport(
                "retrieval-benchmark-v2",
                "2026-07-23.1",
                "bundle-sha256",
                "2026-07-23",
                "policy-v1",
                "model-sha256",
                evaluations,
                metrics
        );
    }

    private RetrievalBenchmarkReport report(
            List<RetrievalRouteEvaluation> evaluations,
            Map<RetrievalBenchmarkRoute, RetrievalBenchmarkMetrics> metrics,
            RetrievalBenchmarkRunMetadata metadata
    ) {
        return new RetrievalBenchmarkReport(
                "retrieval-benchmark-v2",
                "2026-07-23.1",
                "bundle-sha256",
                "2026-07-23",
                "policy-v1",
                "model-sha256",
                evaluations,
                metrics,
                metadata
        );
    }

    private List<RetrievalRouteEvaluation> evaluations() {
        return List.of(
                evaluation(RetrievalBenchmarkRoute.HYBRID, "case-b", RetrievalBenchmarkCategory.SEMANTIC_PARAPHRASE,
                        RetrievalDecisionType.SUFFICIENT, RetrievalDecisionType.SUFFICIENT, 3),
                evaluation(RetrievalBenchmarkRoute.KEYWORD, "case-b", RetrievalBenchmarkCategory.EXACT_TERM,
                        RetrievalDecisionType.INSUFFICIENT, RetrievalDecisionType.SUFFICIENT, null),
                evaluation(RetrievalBenchmarkRoute.VECTOR, "case-a", RetrievalBenchmarkCategory.EXACT_TERM,
                        RetrievalDecisionType.SUFFICIENT, RetrievalDecisionType.SUFFICIENT, null),
                evaluation(RetrievalBenchmarkRoute.VECTOR, "case-c", RetrievalBenchmarkCategory.EXACT_TERM,
                        RetrievalDecisionType.SUFFICIENT, RetrievalDecisionType.SUFFICIENT, 6),
                evaluation(RetrievalBenchmarkRoute.KEYWORD, "case-a", RetrievalBenchmarkCategory.EXACT_TERM,
                        RetrievalDecisionType.SUFFICIENT, RetrievalDecisionType.SUFFICIENT, 1)
        );
    }

    private Map<RetrievalBenchmarkRoute, RetrievalBenchmarkMetrics> metrics() {
        Map<RetrievalBenchmarkRoute, RetrievalBenchmarkMetrics> result = new LinkedHashMap<>();
        result.put(RetrievalBenchmarkRoute.KEYWORD,
                new RetrievalBenchmarkMetrics(2, 0.5, 1.0, 0.75, 1, 0.5, 1));
        result.put(RetrievalBenchmarkRoute.VECTOR,
                new RetrievalBenchmarkMetrics(1, 0.0, 0.0, 0.0, 1, 1.0, 0));
        result.put(RetrievalBenchmarkRoute.HYBRID,
                new RetrievalBenchmarkMetrics(1, 0.0, 1.0, 1.0 / 3.0, 1, 1.0, 0));
        return result;
    }

    private RetrievalRouteEvaluation evaluation(
            RetrievalBenchmarkRoute route,
            String caseId,
            RetrievalBenchmarkCategory category,
            RetrievalDecisionType expectedDecision,
            RetrievalDecisionType actualDecision,
            Integer expectedRank
    ) {
        return new RetrievalRouteEvaluation(
                route,
                caseId,
                RetrievalBenchmarkSplit.HOLDOUT,
                category,
                expectedDecision,
                actualDecision,
                expectedRank,
                List.of("private-claim-id"),
                List.of("private-chunk-id")
        );
    }

    private RetrievalRouteEvaluation evaluation(
            RetrievalBenchmarkRoute route,
            String caseId,
            RetrievalBenchmarkSplit split,
            RetrievalBenchmarkCategory category,
            String subjectSlug,
            RetrievalDecisionType expectedDecision,
            RetrievalDecisionType actualDecision,
            Integer expectedRank
    ) {
        return new RetrievalRouteEvaluation(
                route,
                caseId,
                split,
                category,
                ClaimSubjectType.PROJECT,
                subjectSlug,
                expectedDecision,
                actualDecision,
                expectedRank,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
