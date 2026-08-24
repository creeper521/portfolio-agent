package com.portfolio.agent.evaluation.application;

import com.portfolio.agent.evaluation.domain.AnswerResolution;
import com.portfolio.agent.evaluation.domain.ConversationAnswerScope;
import com.portfolio.agent.common.observability.GenerationMode;
import com.portfolio.agent.evaluation.domain.AnswerSource;
import com.portfolio.agent.evaluation.domain.EvalAnswerShape;
import com.portfolio.agent.evaluation.domain.EvalCase;
import com.portfolio.agent.evaluation.domain.EvalGraderRule;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalMessage;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.evaluation.domain.EvalObservationStatus;
import com.portfolio.agent.evaluation.domain.EvalOrigin;
import com.portfolio.agent.evaluation.domain.EvalProviderUsage;
import com.portfolio.agent.evaluation.domain.EvalRiskLevel;
import com.portfolio.agent.evaluation.domain.EvalRunMode;
import com.portfolio.agent.evaluation.domain.EvalSeverity;
import com.portfolio.agent.evaluation.domain.EvalSplit;
import com.portfolio.agent.evaluation.domain.EvalSubjectRef;
import com.portfolio.agent.evaluation.domain.EvalSuite;
import com.portfolio.agent.evaluation.domain.EvalVerdict;
import com.portfolio.agent.evaluation.execution.EvalExecutionInput;
import com.portfolio.agent.evaluation.execution.EvalExecutor;
import com.portfolio.agent.evaluation.execution.EvalRunContext;
import com.portfolio.agent.evaluation.grading.DeterministicEvalGrader;
import com.portfolio.agent.evaluation.grading.EvalGrader;
import com.portfolio.agent.evaluation.reporting.EvalBaseline;
import com.portfolio.agent.evaluation.reporting.EvalBaselineComparator;
import com.portfolio.agent.evaluation.reporting.EvalMetricAggregator;
import com.portfolio.agent.evaluation.reporting.EvalRunReport;
import com.portfolio.agent.evaluation.reporting.EvalVerdictPolicy;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EvalHarnessTest {

    @Test
    void orchestratesCoveragePlannerExecutionGradingAndVerdict() {
        RecordingExecutor executor = new RecordingExecutor();
        EvalHarness harness = new EvalHarness(
                List.of(executor),
                new DeterministicEvalGrader(),
                new EvalMetricAggregator(),
                new EvalBaselineComparator(),
                new EvalVerdictPolicy(),
                TestBundle.empty());

        EvalCase evalCase = evalCase("case-b", List.of(EvalLayer.BUNDLE_CONTRACT), 3);
        EvalRunReport report = harness.run(
                new EvalSuite("1.0", "suite", "2026-08-06.1", List.of(evalCase)),
                new EvalRunConfig(
                        EvalRunMode.OFFLINE,
                        TestBundle.identity(),
                        TestBundle.policy(),
                        Map.of(),
                        Optional.empty(),
                        com.portfolio.agent.evaluation.domain.EvalProviderAuthorization.NOT_AUTHORIZED,
                        Optional.empty()));

        assertThat(report.getVerdict())
                .as("gates=%s metrics=%s", report.getGates(), report.getMetrics().getAll())
                .isEqualTo(EvalVerdict.PASS);
        assertThat(executor.inputs).hasSize(1);
        assertThat(executor.inputs.get(0).getCaseId()).isEqualTo("case-b");
        assertThat(executor.inputs.get(0).getLayer()).isEqualTo(EvalLayer.BUNDLE_CONTRACT);
        assertThat(executor.inputs.get(0).getTrialIndex()).isEqualTo(1);
    }

    @Test
    void providerLayerRunsThreeTrialsOnlyInProviderMode() {
        RecordingExecutor executor = new RecordingExecutor();
        EvalHarness harness = new EvalHarness(
                List.of(executor),
                new DeterministicEvalGrader(),
                new EvalMetricAggregator(),
                new EvalBaselineComparator(),
                new EvalVerdictPolicy(),
                TestBundle.empty());

        EvalCase evalCase = evalCase("case-p", List.of(EvalLayer.PROVIDER), 3);
        EvalCase providerCase = evalCase("case-p", List.of(EvalLayer.PROVIDER), 3,
                EvalRiskLevel.HIGH);

        harness.run(
                new EvalSuite("1.0", "suite", "2026-08-06.1", List.of(evalCase)),
                new EvalRunConfig(
                        EvalRunMode.OFFLINE,
                        TestBundle.identity(),
                        TestBundle.policy(),
                        Map.of(),
                        Optional.empty(),
                        com.portfolio.agent.evaluation.domain.EvalProviderAuthorization.NOT_AUTHORIZED,
                        Optional.empty()));
        assertThat(executor.inputs).isEmpty();

        RecordingExecutor providerExecutor = new RecordingExecutor();
        EvalHarness providerHarness = new EvalHarness(
                List.of(providerExecutor),
                new DeterministicEvalGrader(),
                new EvalMetricAggregator(),
                new EvalBaselineComparator(),
                new EvalVerdictPolicy(),
                TestBundle.empty());
        providerHarness.run(
                new EvalSuite("1.0", "suite", "2026-08-06.1", List.of(providerCase)),
                new EvalRunConfig(
                        EvalRunMode.PROVIDER,
                        TestBundle.identity(),
                        TestBundle.policy(),
                        Map.of(),
                        Optional.empty(),
                        com.portfolio.agent.evaluation.domain.EvalProviderAuthorization.MOCK_ONLY,
                        Optional.of(EvalVerdict.PASS)));

        assertThat(providerExecutor.inputs).hasSize(3);
        assertThat(providerExecutor.inputs.get(0).getTrialIndex()).isEqualTo(1);
        assertThat(providerExecutor.inputs.get(2).getTrialIndex()).isEqualTo(3);
    }

    @Test
    void executorExceptionBecomesASanitizedErrorObservation() {
        EvalExecutor throwing = new EvalExecutor() {
            @Override
            public boolean supports(EvalLayer layer) {
                return layer == EvalLayer.BUNDLE_CONTRACT;
            }

            @Override
            public EvalObservation execute(
                    EvalExecutionInput input,
                    EvalRunContext context) {
                throw new IllegalStateException("sensitive adapter detail");
            }
        };
        EvalHarness harness = new EvalHarness(
                List.of(throwing),
                new DeterministicEvalGrader(),
                new EvalMetricAggregator(),
                new EvalBaselineComparator(),
                new EvalVerdictPolicy(),
                TestBundle.empty());

        EvalRunReport report = harness.run(
                new EvalSuite("1.0", "suite", "2026-08-06.1",
                        List.of(evalCase("case-e", List.of(EvalLayer.BUNDLE_CONTRACT), 1))),
                new EvalRunConfig(
                        EvalRunMode.OFFLINE,
                        TestBundle.identity(),
                        TestBundle.policy(),
                        Map.of(),
                        Optional.empty(),
                        com.portfolio.agent.evaluation.domain.EvalProviderAuthorization.NOT_AUTHORIZED,
                        Optional.empty()));

        assertThat(report.getObservations()).singleElement().satisfies(observation -> {
            assertThat(observation.getStatus()).isEqualTo(EvalObservationStatus.ERROR);
            assertThat(observation.getReasonCodes()).contains("EXECUTOR_ERROR");
        });
        assertThat(report.getVerdict()).isEqualTo(EvalVerdict.FAIL);
    }

    @Test
    void unsupportedLayerProducesExecutorMissingWithoutSkippingTheCase() {
        EvalHarness harness = new EvalHarness(
                List.of(),
                new DeterministicEvalGrader(),
                new EvalMetricAggregator(),
                new EvalBaselineComparator(),
                new EvalVerdictPolicy(),
                TestBundle.empty());

        EvalRunReport report = harness.run(
                new EvalSuite("1.0", "suite", "2026-08-06.1",
                        List.of(evalCase("case-u", List.of(EvalLayer.BUNDLE_CONTRACT), 1))),
                new EvalRunConfig(
                        EvalRunMode.OFFLINE,
                        TestBundle.identity(),
                        TestBundle.policy(),
                        Map.of(),
                        Optional.empty(),
                        com.portfolio.agent.evaluation.domain.EvalProviderAuthorization.NOT_AUTHORIZED,
                        Optional.empty()));

        assertThat(report.getObservations()).singleElement().satisfies(observation -> {
            assertThat(observation.getStatus()).isEqualTo(EvalObservationStatus.ERROR);
            assertThat(observation.getReasonCodes()).contains("EXECUTOR_MISSING");
        });
    }

    @Test
    void offlineSkipsHttpE2ELayerInsteadOfReportingExecutorMissing() {
        EvalHarness harness = new EvalHarness(
                List.of(),
                new DeterministicEvalGrader(),
                new EvalMetricAggregator(),
                new EvalBaselineComparator(),
                new EvalVerdictPolicy(),
                TestBundle.empty());

        EvalRunReport report = harness.run(
                new EvalSuite("1.0", "suite", "2026-08-06.1",
                        List.of(evalCase("case-http", List.of(EvalLayer.HTTP_E2E), 1))),
                new EvalRunConfig(
                        EvalRunMode.OFFLINE,
                        TestBundle.identity(),
                        TestBundle.policy(),
                        Map.of(),
                        Optional.empty(),
                        com.portfolio.agent.evaluation.domain.EvalProviderAuthorization.NOT_AUTHORIZED,
                        Optional.empty()));

        assertThat(report.getObservations())
                .as("offline must skip HTTP_E2E without observations")
                .isEmpty();
    }

    @Test
    void validateModeNeverCallsExecutors() {
        RecordingExecutor executor = new RecordingExecutor();
        EvalHarness harness = new EvalHarness(
                List.of(executor),
                new DeterministicEvalGrader(),
                new EvalMetricAggregator(),
                new EvalBaselineComparator(),
                new EvalVerdictPolicy(),
                TestBundle.empty());

        EvalRunReport report = harness.run(
                new EvalSuite("1.0", "suite", "2026-08-06.1",
                        List.of(referenceFreeCase("case-v"))),
                new EvalRunConfig(
                        EvalRunMode.VALIDATE,
                        TestBundle.identity(),
                        TestBundle.policy(),
                        Map.of(),
                        Optional.empty(),
                        com.portfolio.agent.evaluation.domain.EvalProviderAuthorization.NOT_AUTHORIZED,
                        Optional.empty()));

        assertThat(executor.inputs).isEmpty();
        assertThat(report.getVerdict()).isEqualTo(EvalVerdict.PASS);
    }

    /**
     * A case without subject/claim/evidence references: with an empty bundle
     * there is nothing to violate, so validate only proves non-emptiness.
     */
    private EvalCase referenceFreeCase(String id) {
        return new EvalCase(
                id, "Case " + id, EvalSplit.HOLDOUT, EvalOrigin.HUMAN_AUTHORED,
                EvalRiskLevel.STANDARD, "APPROVED", "reviewer", "TEST", "test",
                "2026-08-04.1", List.of("test"),
                new EvalCase.Input(List.of(new EvalMessage("user", "Test question"))),
                new EvalCase.Oracle(List.of()),
                new EvalCase.Expectations(
                        List.of(AnswerResolution.ANSWERED),
                        List.of(ConversationAnswerScope.PORTFOLIO),
                        List.of(), List.of(), List.of(), List.of()),
                new EvalCase.Execution(layers(1), 1),
                List.of(new EvalGraderRule("SUBJECT_MATCH", EvalSeverity.BLOCKING)),
                new EvalCase.Maintenance(List.of(), true));
    }

    private List<EvalLayer> layers(int providerTrials) {
        return List.of(EvalLayer.BUNDLE_CONTRACT);
    }

    @Test
    void validateFailsOnUnknownSubjectReferences() {
        EvalHarness harness = new EvalHarness(
                List.of(new RecordingExecutor()),
                new DeterministicEvalGrader(),
                new EvalMetricAggregator(),
                new EvalBaselineComparator(),
                new EvalVerdictPolicy(),
                TestBundle.empty());

        EvalCase caseWithGhost = new EvalCase(
                "case-ghost", "Case ghost", EvalSplit.HOLDOUT,
                EvalOrigin.HUMAN_AUTHORED, EvalRiskLevel.STANDARD,
                "APPROVED", "reviewer", "TEST", "test", "2026-08-04.1",
                List.of("test"),
                new EvalCase.Input(List.of(new EvalMessage("user", "Test question"))),
                new EvalCase.Oracle(List.of(new EvalSubjectRef(
                        ClaimSubjectType.CASE, "ghost-subject"))),
                new EvalCase.Expectations(
                        List.of(AnswerResolution.ANSWERED),
                        List.of(ConversationAnswerScope.PORTFOLIO),
                        List.of(), List.of(), List.of(), List.of()),
                new EvalCase.Execution(List.of(EvalLayer.BUNDLE_CONTRACT), 1),
                List.of(new EvalGraderRule("SUBJECT_MATCH", EvalSeverity.BLOCKING)),
                new EvalCase.Maintenance(List.of(), true));

        EvalRunReport report = harness.run(
                new EvalSuite("1.0", "suite", "2026-08-06.1", List.of(caseWithGhost)),
                new EvalRunConfig(
                        EvalRunMode.VALIDATE,
                        TestBundle.identity(),
                        TestBundle.policy(),
                        Map.of(),
                        Optional.empty(),
                        com.portfolio.agent.evaluation.domain.EvalProviderAuthorization.NOT_AUTHORIZED,
                        Optional.empty()));

        assertThat(report.getVerdict()).isEqualTo(EvalVerdict.FAIL);
        assertThat(report.getGates().stream()
                .anyMatch(gate -> gate.getMetricName()
                        .equals("dataset.referenceViolations")
                        && !gate.isPassed())).isTrue();
    }

    private EvalCase evalCase(String id, List<EvalLayer> layers, int providerTrials) {
        return evalCase(id, layers, providerTrials, EvalRiskLevel.STANDARD);
    }

    private EvalCase evalCase(
            String id,
            List<EvalLayer> layers,
            int providerTrials,
            EvalRiskLevel riskLevel) {
        EvalSubjectRef subject = new EvalSubjectRef(ClaimSubjectType.CASE, "case-a");
        return new EvalCase(
                id, "Case " + id, EvalSplit.HOLDOUT, EvalOrigin.HUMAN_AUTHORED,
                riskLevel, "APPROVED", "reviewer", "TEST", "test", "2026-08-04.1",
                List.of("test"),
                new EvalCase.Input(List.of(new EvalMessage("user", "Test question"))),
                new EvalCase.Oracle(List.of(subject)),
                new EvalCase.Expectations(
                        List.of(AnswerResolution.ANSWERED),
                        List.of(ConversationAnswerScope.PORTFOLIO),
                        List.of("claim-1"),
                        List.of("E-01"),
                        List.of(),
                        List.of()),
                new EvalCase.Execution(layers, providerTrials),
                List.of(new EvalGraderRule("SUBJECT_MATCH", EvalSeverity.BLOCKING),
                        new EvalGraderRule("ANSWER_QUALITY", EvalSeverity.SCORED)),
                new EvalCase.Maintenance(List.of(subject), true));
    }

    private static final class RecordingExecutor implements EvalExecutor {
        private final List<EvalExecutionInput> inputs = new ArrayList<>();

        @Override
        public boolean supports(EvalLayer layer) {
            return layer == EvalLayer.BUNDLE_CONTRACT || layer == EvalLayer.PROVIDER;
        }

        @Override
        public EvalObservation execute(
                EvalExecutionInput input,
                EvalRunContext context) {
            inputs.add(input);
            return new EvalObservation(
                    input.getCaseId(), input.getLayer(), input.getTrialIndex(),
                    EvalObservationStatus.PASS,
                    null, "case-a",
                    List.of("claim-1"), List.of("E-01"), List.of(),
                    AnswerResolution.ANSWERED, ConversationAnswerScope.PORTFOLIO,
                    GenerationMode.DETERMINISTIC, AnswerSource.RETRIEVAL,
                    List.of("DETERMINISTIC"), 12L,
                    EvalProviderUsage.unavailable(),
                    EvalAnswerShape.empty(), false, false);
        }
    }

    private static final class TestBundle {
        private static com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot empty() {
            com.portfolio.agent.portfolio.domain.PortfolioSnapshot content =
                    new com.portfolio.agent.portfolio.domain.PortfolioSnapshot(
                            "1.0", "2026-08-06.1", java.time.OffsetDateTime.now(),
                            null, List.of(), List.of(), List.of(), List.of(),
                            List.of(), List.of(), List.of(), List.of());
            return new com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot(
                    content, "sha256:runtime", java.time.Instant.now());
        }

        private static com.portfolio.agent.evaluation.domain.EvalRunIdentity identity() {
            return com.portfolio.agent.evaluation.domain.EvalRunIdentity.create(
                    "deadbeef", "2026-08-06.1", "sha256:dataset",
                    "2026-08-05.1", "sha256:bundle", "sha256:prompt",
                    "sha256:retrieval", "BGE-small-zh-v1.5", "sha256:embedding",
                    "NOT_APPLICABLE", "NOT_APPLICABLE", "sha256:model-params",
                    "NOT_APPLICABLE", "NOT_APPLICABLE");
        }

        private static com.portfolio.agent.evaluation.domain.EvalPolicy policy() {
            return com.portfolio.agent.evaluation.domain.EvalPolicy.builder()
                    .policyId("phase-0.v1")
                    .mode("OFFLINE")
                    .blockingProvider("glm-4-7-flash")
                    .publicSubjectSmokeCoverageMinimum(new BigDecimal("1.0"))
                    .namedRouteTopOneMinimum(new BigDecimal("1.0"))
                    .deepSemanticRouteTopOneMinimum(new BigDecimal("0.9"))
                    .priorityDeepSemanticRouteTopOneMinimum(new BigDecimal("0.95"))
                    .retrievalHitAtFiveMinimum(new BigDecimal("0.9"))
                    .requiredClaimRecallMinimum(new BigDecimal("0.9"))
                    .providerTrialPassRateMinimum(new BigDecimal("0.9"))
                    .providerScenarioPassRateMinimum(new BigDecimal("0.9"))
                    .safetyBoundaryPassRateMinimum(new BigDecimal("1.0"))
                    .falseSufficientMaximum(new BigDecimal("0.0"))
                    .providerFailureRateMaximum(new BigDecimal("0.02"))
                    .providerP95LatencyMaximumMs(20_000L)
                    .priorityMetricRegressionMaximum(new BigDecimal("0.02"))
                    .globalMetricRegressionMaximum(new BigDecimal("0.03"))
                    .answerQualityPassRateMinimum(new BigDecimal("0.8"))
                    .defaultTrials(3)
                    .standardMinimumPasses(2)
                    .highMinimumPasses(3)
                    .invariantMinimumPasses(3)
                    .pricingCurrency("CNY")
                    .pricingBudget(new BigDecimal("5.0"))
                    .build();
        }
    }
}
