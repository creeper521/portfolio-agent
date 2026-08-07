package com.portfolio.agent.evaluation.dataset;

import com.portfolio.agent.evaluation.domain.EvalPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvalPolicyLoaderTest {

    @TempDir
    Path tempDir;

    private final EvalPolicyLoader loader = new EvalPolicyLoader();

    private static final String VALID_POLICY = """
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

    private Path write(String name, String content) throws Exception {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    @Test
    void loadsACompletePolicyWithExplicitValueTypes() throws Exception {
        EvalPolicy policy = loader.load(write("valid.json", VALID_POLICY));

        assertThat(policy.getPolicyId()).isEqualTo("phase-0.v1");
        assertThat(policy.getBlockingProvider()).isEqualTo("DEEPSEEK_V4_FLASH");
        assertThat(policy.getPublicSubjectSmokeCoverageMinimum()).isEqualByComparingTo("1.0");
        assertThat(policy.getProviderP95LatencyMaximumMs()).isEqualTo(20_000L);
        assertThat(policy.getProviderFailureRateMaximum()).isEqualByComparingTo("0.02");
        assertThat(policy.getDefaultTrials()).isEqualTo(3);
        assertThat(policy.getInvariantMinimumPasses()).isEqualTo(3);
        assertThat(policy.getAnswerQualityPassRateMinimum()).isEqualByComparingTo("0.8");
        assertThat(policy.getPricingCurrency()).isEqualTo("CNY");
        assertThat(policy.getPricingBudget()).isEqualByComparingTo("5.0");
    }

    @Test
    void rejectsUnknownFields() throws Exception {
        String json = VALID_POLICY.replace(
                "\"policyId\": \"phase-0.v1\"",
                "\"policyId\": \"phase-0.v1\",\n  \"unexpectedField\": true");

        assertThatThrownBy(() -> loader.load(write("unknown.json", json)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid evaluation policy");
    }

    @Test
    void rejectsNegativeP95Latency() throws Exception {
        String json = VALID_POLICY.replace(
                "\"providerP95LatencyMaximumMs\": 20000",
                "\"providerP95LatencyMaximumMs\": -1");

        assertThatThrownBy(() -> loader.load(write("negative-p95.json", json)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid evaluation policy");
    }

    @Test
    void rejectsMissingP95Latency() throws Exception {
        String json = VALID_POLICY.replace(
                "\"providerP95LatencyMaximumMs\": 20000,\n", "");

        assertThatThrownBy(() -> loader.load(write("missing-p95.json", json)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid evaluation policy");
    }

    @Test
    void rejectsMinimumPassesExceedingTrials() throws Exception {
        String json = VALID_POLICY.replace(
                "\"standardMinimumPasses\": 2",
                "\"standardMinimumPasses\": 4");

        assertThatThrownBy(() -> loader.load(write("passes-over-trials.json", json)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid evaluation policy");
    }

    @Test
    void rejectsRatioOutsideZeroToOne() throws Exception {
        String json = VALID_POLICY.replace(
                "\"deepSemanticRouteTopOneMinimum\": 0.9",
                "\"deepSemanticRouteTopOneMinimum\": 1.1");

        assertThatThrownBy(() -> loader.load(write("ratio-out-of-range.json", json)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid evaluation policy");
    }

    @Test
    void rejectsDuplicatePolicyIdsAcrossFiles() throws Exception {
        Path first = write("first.json", VALID_POLICY);
        Path second = write("second.json", VALID_POLICY);

        assertThatThrownBy(() -> loader.loadAll(List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid evaluation policy");
    }

    @Test
    void loadAllAcceptsDistinctPolicyIds() throws Exception {
        Path first = write("first.json", VALID_POLICY);
        Path second = write("second.json",
                VALID_POLICY.replace("\"phase-0.v1\"", "\"phase-0.v2\""));

        List<EvalPolicy> policies = loader.loadAll(List.of(first, second));

        assertThat(policies).hasSize(2);
        assertThat(policies.get(1).getPolicyId()).isEqualTo("phase-0.v2");
    }
}
