package com.portfolio.agent.evaluation.application;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationDraft;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.domain.ConversationRoute;
import com.portfolio.agent.answer.domain.ConversationSourceScope;
import com.portfolio.agent.answer.domain.ConversationWindow;
import com.portfolio.agent.answer.domain.PortfolioGroundingContext;
import com.portfolio.agent.answer.gateway.ConversationalModelPort;
import com.portfolio.agent.evaluation.domain.EvalCase;
import com.portfolio.agent.evaluation.domain.EvalGraderRule;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalMessage;
import com.portfolio.agent.evaluation.domain.EvalOrigin;
import com.portfolio.agent.evaluation.domain.EvalProviderAuthorization;
import com.portfolio.agent.evaluation.domain.EvalRiskLevel;
import com.portfolio.agent.evaluation.domain.EvalRunMode;
import com.portfolio.agent.evaluation.domain.EvalSeverity;
import com.portfolio.agent.evaluation.domain.EvalSplit;
import com.portfolio.agent.evaluation.domain.EvalSubjectRef;
import com.portfolio.agent.evaluation.domain.EvalSuite;
import com.portfolio.agent.evaluation.domain.EvalVerdict;
import com.portfolio.agent.evaluation.execution.ProviderEvalExecutor;
import com.portfolio.agent.evaluation.grading.DeterministicEvalGrader;
import com.portfolio.agent.evaluation.reporting.EvalBaselineComparator;
import com.portfolio.agent.evaluation.reporting.EvalMetricAggregator;
import com.portfolio.agent.evaluation.reporting.EvalRunReport;
import com.portfolio.agent.evaluation.reporting.EvalVerdictPolicy;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProviderEvalHarnessTest {

    @Test
    void providerModeRunsThreeTrialsThroughTheMockSeamAndReportsPass() {
        ConversationalModelPort port = mock(ConversationalModelPort.class);
        when(port.generate(any(String.class), any(ConversationWindow.class),
                any(ConversationRoute.class), any(PortfolioGroundingContext.class)))
                .thenReturn(ConversationModelResult.success(new ConversationDraft(
                        "t", AnswerResolution.ANSWERED, List.of(
                                new ConversationAnswerBlock(
                                        ConversationSourceScope.PORTFOLIO,
                                        "回答正文", List.of("claim-1"), List.of("E-01"))))));
        ProviderEvalExecutor providerExecutor =
                new ProviderEvalExecutor(port, "mock-provider");

        PortfolioSnapshot content = new PortfolioSnapshot(
                "1.0", "2026-08-06.1", OffsetDateTime.now(),
                null, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
        RuntimeContentSnapshot bundle = new RuntimeContentSnapshot(
                content, "sha256:runtime", java.time.Instant.now());
        EvalHarness harness = new EvalHarness(
                List.of(providerExecutor),
                new DeterministicEvalGrader(),
                new EvalMetricAggregator(),
                new EvalBaselineComparator(),
                new EvalVerdictPolicy(),
                bundle);

        EvalCase evalCase = providerCase("case-p1");
        EvalRunReport report = harness.run(
                new EvalSuite("1.0", "suite", "2026-08-06.1", List.of(evalCase)),
                new EvalRunConfig(
                        EvalRunMode.PROVIDER,
                        identity(),
                        policy(),
                        Map.of(),
                        Optional.empty(),
                        EvalProviderAuthorization.REAL_AUTHORIZED,
                        Optional.of(EvalVerdict.PASS)));

        assertThat(report.getObservations()).hasSize(3);
        assertThat(report.getObservations()).allSatisfy(observation ->
                assertThat(observation.isProviderInvoked()).isTrue());
        assertThat(report.getObservations()).allSatisfy(observation ->
                // usage stays unavailable: the mock seam returns no real tokens
                assertThat(observation.getProviderUsage().isAvailable()).isFalse());
        java.math.BigDecimal trialRate =
                report.getMetrics().getValue("provider.trialPassRate").getValue();
        if (trialRate.compareTo(new java.math.BigDecimal("1.0")) != 0) {
            throw new AssertionError(
                    "trialPassRate=" + trialRate + " grades=" + report.getGrades()
                            + " observations=" + report.getObservations());
        }
        assertThat(report.getVerdict()).isEqualTo(EvalVerdict.PASS);
    }

    @Test
    void providerFailuresAcrossTrialsFailTheProviderGate() {
        ConversationalModelPort port = mock(ConversationalModelPort.class);
        when(port.generate(any(String.class), any(ConversationWindow.class),
                any(ConversationRoute.class), any(PortfolioGroundingContext.class)))
                .thenReturn(com.portfolio.agent.answer.domain.ConversationModelResult
                        .failure(com.portfolio.agent.answer.domain
                                .ConversationModelFailureCode.TIMEOUT));
        ProviderEvalExecutor providerExecutor =
                new ProviderEvalExecutor(port, "mock-provider");
        PortfolioSnapshot content = new PortfolioSnapshot(
                "1.0", "2026-08-06.1", OffsetDateTime.now(),
                null, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
        RuntimeContentSnapshot bundle = new RuntimeContentSnapshot(
                content, "sha256:runtime", java.time.Instant.now());
        EvalHarness harness = new EvalHarness(
                List.of(providerExecutor),
                new DeterministicEvalGrader(),
                new EvalMetricAggregator(),
                new EvalBaselineComparator(),
                new EvalVerdictPolicy(),
                bundle);

        EvalRunReport report = harness.run(
                new EvalSuite("1.0", "suite", "2026-08-06.1",
                        List.of(providerCase("case-p2"))),
                new EvalRunConfig(
                        EvalRunMode.PROVIDER,
                        identity(),
                        policy(),
                        Map.of(),
                        Optional.empty(),
                        EvalProviderAuthorization.REAL_AUTHORIZED,
                        Optional.of(EvalVerdict.PASS)));

        assertThat(report.getVerdict()).isEqualTo(EvalVerdict.FAIL);
        assertThat(report.getGates()).anySatisfy(gate ->
                assertThat(gate.getMetricName()).isEqualTo("provider.trialPassRate"));
    }

    private EvalCase providerCase(String id) {
        EvalSubjectRef subject = new EvalSubjectRef(ClaimSubjectType.PROJECT, "sql-audit");
        return new EvalCase(
                id, "Provider case", EvalSplit.HOLDOUT, EvalOrigin.HUMAN_AUTHORED,
                EvalRiskLevel.HIGH, "APPROVED", "reviewer", "TEST", "test",
                "2026-08-06.1", List.of("test"),
                new EvalCase.Input(List.of(new EvalMessage(
                        "user", "请介绍 SQL 审计与故障排查工具项目"))),
                new EvalCase.Oracle(List.of(subject)),
                new EvalCase.Expectations(
                        List.of(AnswerResolution.ANSWERED),
                        List.of(ConversationAnswerScope.PORTFOLIO),
                        List.of("claim-1"),
                        List.of("E-01"),
                        List.of(),
                        List.of()),
                new EvalCase.Execution(List.of(EvalLayer.PROVIDER), 3),
                List.of(new EvalGraderRule("REQUIRED_CLAIMS", EvalSeverity.BLOCKING),
                        new EvalGraderRule("ANSWER_QUALITY", EvalSeverity.SCORED)),
                new EvalCase.Maintenance(List.of(subject), true));
    }

    private com.portfolio.agent.evaluation.domain.EvalRunIdentity identity() {
        return com.portfolio.agent.evaluation.domain.EvalRunIdentity.create(
                "deadbeef", "2026-08-06.1", "sha256:dataset",
                "2026-08-05.1", "sha256:bundle", "sha256:prompt",
                "sha256:retrieval", "BGE-small-zh-v1.5", "sha256:embedding",
                "DEEPSEEK_V4_FLASH", "deepseek-v4-flash", "sha256:model-params",
                "judge-v1", "rubric-v1");
    }

    private com.portfolio.agent.evaluation.domain.EvalPolicy policy() {
        return com.portfolio.agent.evaluation.domain.EvalPolicy.builder()
                .policyId("phase-0.v1")
                .mode("OFFLINE")
                .blockingProvider("DEEPSEEK_V4_FLASH")
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
