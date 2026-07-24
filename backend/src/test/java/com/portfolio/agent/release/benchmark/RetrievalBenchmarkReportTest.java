package com.portfolio.agent.release.benchmark;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.portfolio.agent.answer.domain.RetrievalDecisionType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalBenchmarkReportTest {

    private final ObjectMapper canonicalMapper = new ObjectMapper()
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
    void rendersNeutralStableMarkdownWithMetricsCategoriesAndFailures() {
        RetrievalBenchmarkReport report = report(evaluations(), metrics());

        String markdown = new RetrievalBenchmarkMarkdownRenderer().render(report);

        assertThat(markdown).startsWith("# Retrieval Baseline Comparison\n");
        assertThat(markdown).contains(
                "- Suite version: `retrieval-benchmark-v2`",
                "- Content version: `2026-07-23.1`",
                "- Bundle hash: `bundle-sha256`",
                "- Policy version: `policy-v1`",
                "- Model descriptor hash: `model-sha256`",
                "| Route | Positive cases | Hit@1 | Hit@5 | MRR@5 | False sufficient |",
                "| KEYWORD | 2 | 0.5000 | 1.0000 | 0.7500 | 1 |",
                "| Category | Route | Positive cases | Hit@1 | Hit@5 | MRR@5 | False sufficient |",
                "| EXACT_TERM | KEYWORD | 1 | 1.0000 | 1.0000 | 1.0000 | 1 |",
                "- KEYWORD: `case-b`",
                "- VECTOR: `case-a`",
                "- VECTOR: `case-c`");
        assertThat(markdown).doesNotContainIgnoringCase("hybrid is valuable", "hybrid provides value");
    }

    private RetrievalBenchmarkReport report(
            List<RetrievalRouteEvaluation> evaluations,
            Map<RetrievalBenchmarkRoute, RetrievalBenchmarkMetrics> metrics
    ) {
        return new RetrievalBenchmarkReport(
                "retrieval-benchmark-v2",
                "2026-07-23.1",
                "bundle-sha256",
                "policy-v1",
                "model-sha256",
                evaluations,
                metrics
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
        result.put(RetrievalBenchmarkRoute.KEYWORD, new RetrievalBenchmarkMetrics(2, 0.5, 1.0, 0.75, 1));
        result.put(RetrievalBenchmarkRoute.VECTOR, new RetrievalBenchmarkMetrics(1, 0.0, 0.0, 0.0, 0));
        result.put(RetrievalBenchmarkRoute.HYBRID, new RetrievalBenchmarkMetrics(1, 0.0, 1.0, 1.0 / 3.0, 0));
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
}
