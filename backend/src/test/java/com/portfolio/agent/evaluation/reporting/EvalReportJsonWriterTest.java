package com.portfolio.agent.evaluation.reporting;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.evaluation.domain.EvalAnswerShape;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.evaluation.domain.EvalObservationStatus;
import com.portfolio.agent.evaluation.domain.EvalProviderUsage;
import com.portfolio.agent.evaluation.domain.EvalRunIdentity;
import com.portfolio.agent.evaluation.domain.EvalRunMode;
import com.portfolio.agent.evaluation.domain.EvalSeverity;
import com.portfolio.agent.evaluation.domain.EvalVerdict;
import com.portfolio.agent.evaluation.grading.EvalGrade;
import com.portfolio.agent.evaluation.grading.EvalReasonCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EvalReportJsonWriterTest {

    private final EvalReportJsonWriter writer = new EvalReportJsonWriter();

    @Test
    void serializationIsByteForByteStable() {
        EvalRunReport report = reportFixture();

        String first = writer.write(report);
        String second = writer.write(report);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void reportNeverContainsSensitiveFields() {
        String json = writer.write(reportFixture());

        for (String forbidden : List.of(
                "\"question\"", "\"messages\"", "\"rawAnswer\"", "\"prompt\"",
                "\"stack\"", "\"credential\"", "\"path\"", "\"body\"", "\"visitor\"")) {
            assertThat(json).as("must not contain %s", forbidden)
                    .doesNotContain(forbidden);
        }
    }

    @Test
    void canonicalDocumentContainsStableKeysAndSortedEntries() {
        String json = writer.write(reportFixture());

        assertThat(json).contains("\"runId\"");
        assertThat(json).contains("\"verdict\"");
        assertThat(json).contains("\"hardError.count\"");
        assertThat(json).contains("\"routing.top1\"");
        assertThat(json).contains("\"metricName\"");
        assertThat(json).contains("\"reasonCode\"");
        assertThat(json).contains("\"directAnswerPresent\"");
        assertThat(json.indexOf("\"hardError.count\""))
                .isLessThan(json.indexOf("\"routing.top1\""));
    }

    @Test
    void writeWithExpandedCasesEmbedsTheFullCaseManifest() {
        com.portfolio.agent.evaluation.domain.EvalCase generated =
                new com.portfolio.agent.evaluation.domain.EvalCase(
                        "smoke.project.sql-audit", "SQL 审计与故障排查工具",
                        com.portfolio.agent.evaluation.domain.EvalSplit.CALIBRATION,
                        com.portfolio.agent.evaluation.domain.EvalOrigin.BUNDLE_GENERATED,
                        com.portfolio.agent.evaluation.domain.EvalRiskLevel.STANDARD,
                        "APPROVED", "phase-0-generator", "PUBLIC_BUNDLE",
                        "公开主体冒烟（自动生成）", "2026-08-06.1",
                        List.of("smoke", "generated"),
                        new com.portfolio.agent.evaluation.domain.EvalCase.Input(
                                List.of(new com.portfolio.agent.evaluation.domain
                                        .EvalMessage("user", "SQL 审计与故障排查工具"))),
                        new com.portfolio.agent.evaluation.domain.EvalCase.Oracle(
                                List.of(new com.portfolio.agent.evaluation.domain
                                        .EvalSubjectRef(
                                        com.portfolio.agent.portfolio.domain
                                                .ClaimSubjectType.PROJECT, "sql-audit"))),
                        new com.portfolio.agent.evaluation.domain.EvalCase.Expectations(
                                List.of(AnswerResolution.ANSWERED),
                                List.of(ConversationAnswerScope.PORTFOLIO),
                                List.of(), List.of(), List.of(), List.of()),
                        new com.portfolio.agent.evaluation.domain.EvalCase.Execution(
                                List.of(EvalLayer.BUNDLE_CONTRACT), 3),
                        List.of(new com.portfolio.agent.evaluation.domain.EvalGraderRule(
                                "SUBJECT_MATCH",
                                com.portfolio.agent.evaluation.domain.EvalSeverity.BLOCKING)),
                        new com.portfolio.agent.evaluation.domain.EvalCase.Maintenance(
                                List.of(new com.portfolio.agent.evaluation.domain
                                        .EvalSubjectRef(
                                        com.portfolio.agent.portfolio.domain
                                                .ClaimSubjectType.PROJECT, "sql-audit")),
                                true));

        String json = writer.write(reportFixture(), List.of(generated));

        assertThat(json).contains("\"expandedCases\"");
        assertThat(json).contains("\"smoke.project.sql-audit\"");
        assertThat(json).contains("\"BUNDLE_GENERATED\"");
        // the fixture is NOT_AUTHORIZED: real provider did not run
        assertThat(json).contains("\"providerRealState\":\"INCOMPLETE\"");
    }

    private EvalRunReport reportFixture() {
        EvalRunIdentity identity = EvalRunIdentity.create(
                "deadbeef", "2026-08-06.1", "sha256:dataset",
                "2026-08-05.1", "sha256:bundle", "sha256:prompt",
                "sha256:retrieval", "BGE-small-zh-v1.5", "sha256:embedding",
                "NOT_APPLICABLE", "NOT_APPLICABLE", "sha256:model-params",
                "NOT_APPLICABLE", "NOT_APPLICABLE");
        EvalObservation observation = new EvalObservation(
                "case-b", EvalLayer.INTELLIGENCE, 1, EvalObservationStatus.PASS,
                null, "case-a", List.of("claim-1"), List.of("E-01"), List.of(),
                AnswerResolution.ANSWERED, ConversationAnswerScope.PORTFOLIO,
                GenerationMode.DETERMINISTIC, AnswerSource.RETRIEVAL,
                List.of("DETERMINISTIC"), 12L, EvalProviderUsage.unavailable(),
                EvalAnswerShape.empty(), false, false);
        EvalGrade grade = new EvalGrade(
                "case-b", EvalLayer.INTELLIGENCE, 1, "SUBJECT_MATCH",
                EvalSeverity.BLOCKING, true, EvalReasonCode.PASS, 1L, 1L);
        EvalGateResult gate = new EvalGateResult(
                "routing.top1", new BigDecimal("1.0"), new BigDecimal("0.9"),
                EvalGateResult.EvalComparisonOperator.GE, true, EvalSeverity.BLOCKING,
                EvalReasonCode.PASS);
        EvalMetrics metrics = new EvalMetrics(Map.of(
                "routing.top1", new EvalMetrics.MetricValue(
                        new BigDecimal("1.0"), 1L, 1L),
                "hardError.count", new EvalMetrics.MetricValue(
                        BigDecimal.ZERO, 0L, 1L)));
        EvalComparison comparison = new EvalComparison(
                false, Map.of(), List.of(), List.of());
        return new EvalRunReport(
                "run-test", EvalRunMode.OFFLINE, identity, EvalVerdict.PASS,
                metrics, comparison, List.of(gate), List.of(observation),
                List.of(grade), Optional.empty(),
                com.portfolio.agent.evaluation.domain.EvalProviderAuthorization.NOT_AUTHORIZED);
    }
}
