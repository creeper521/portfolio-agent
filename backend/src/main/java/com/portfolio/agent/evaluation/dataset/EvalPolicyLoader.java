package com.portfolio.agent.evaluation.dataset;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.evaluation.domain.EvalPolicy;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class EvalPolicyLoader {

    private static final BigDecimal ZERO = new BigDecimal("0.0");
    private static final BigDecimal ONE = new BigDecimal("1.0");

    private final ObjectMapper mapper;

    public EvalPolicyLoader() {
        this.mapper = new ObjectMapper()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public EvalPolicy load(Path path) {
        Objects.requireNonNull(path, "path");
        try {
            String json = Files.readString(path);
            PolicyDocument document = mapper.readValue(json, PolicyDocument.class);
            return toPolicy(document);
        } catch (IOException | RuntimeException cause) {
            throw new IllegalArgumentException("Invalid evaluation policy", cause);
        }
    }

    public List<EvalPolicy> loadAll(List<Path> paths) {
        Objects.requireNonNull(paths, "paths");
        List<EvalPolicy> policies = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (Path path : paths) {
            EvalPolicy policy = load(path);
            if (!seenIds.add(policy.getPolicyId())) {
                throw new IllegalArgumentException("Invalid evaluation policy");
            }
            policies.add(policy);
        }
        return policies;
    }

    private EvalPolicy toPolicy(PolicyDocument document) {
        requireRatio(document.thresholds.blocking.publicSubjectSmokeCoverageMinimum);
        requireRatio(document.thresholds.blocking.namedRouteTopOneMinimum);
        requireRatio(document.thresholds.blocking.deepSemanticRouteTopOneMinimum);
        requireRatio(document.thresholds.blocking.priorityDeepSemanticRouteTopOneMinimum);
        requireRatio(document.thresholds.blocking.retrievalHitAtFiveMinimum);
        requireRatio(document.thresholds.blocking.requiredClaimRecallMinimum);
        requireRatio(document.thresholds.blocking.providerTrialPassRateMinimum);
        requireRatio(document.thresholds.blocking.providerScenarioPassRateMinimum);
        requireRatio(document.thresholds.blocking.safetyBoundaryPassRateMinimum);
        requireRatio(document.thresholds.blocking.falseSufficientMaximum);
        requireRatio(document.thresholds.blocking.providerFailureRateMaximum);
        if (document.thresholds.blocking.providerP95LatencyMaximumMs < 0L) {
            throw new IllegalArgumentException("Invalid evaluation policy");
        }
        requireRatio(document.thresholds.blocking.priorityMetricRegressionMaximum);
        requireRatio(document.thresholds.blocking.globalMetricRegressionMaximum);
        requireRatio(document.thresholds.scored.answerQualityPassRateMinimum);
        requireNonNegative(document.pricing.budget);
        if (document.trialPolicy.standardMinimumPasses > document.trialPolicy.defaultTrials
                || document.trialPolicy.highMinimumPasses > document.trialPolicy.defaultTrials
                || document.trialPolicy.invariantMinimumPasses > document.trialPolicy.defaultTrials) {
            throw new IllegalArgumentException("Invalid evaluation policy");
        }
        return EvalPolicy.builder()
                .policyId(document.policyId)
                .mode(document.mode)
                .blockingProvider(document.blockingProvider)
                .publicSubjectSmokeCoverageMinimum(document.thresholds.blocking.publicSubjectSmokeCoverageMinimum)
                .namedRouteTopOneMinimum(document.thresholds.blocking.namedRouteTopOneMinimum)
                .deepSemanticRouteTopOneMinimum(document.thresholds.blocking.deepSemanticRouteTopOneMinimum)
                .priorityDeepSemanticRouteTopOneMinimum(document.thresholds.blocking.priorityDeepSemanticRouteTopOneMinimum)
                .retrievalHitAtFiveMinimum(document.thresholds.blocking.retrievalHitAtFiveMinimum)
                .requiredClaimRecallMinimum(document.thresholds.blocking.requiredClaimRecallMinimum)
                .providerTrialPassRateMinimum(document.thresholds.blocking.providerTrialPassRateMinimum)
                .providerScenarioPassRateMinimum(document.thresholds.blocking.providerScenarioPassRateMinimum)
                .safetyBoundaryPassRateMinimum(document.thresholds.blocking.safetyBoundaryPassRateMinimum)
                .falseSufficientMaximum(document.thresholds.blocking.falseSufficientMaximum)
                .providerFailureRateMaximum(document.thresholds.blocking.providerFailureRateMaximum)
                .providerP95LatencyMaximumMs(document.thresholds.blocking.providerP95LatencyMaximumMs)
                .priorityMetricRegressionMaximum(document.thresholds.blocking.priorityMetricRegressionMaximum)
                .globalMetricRegressionMaximum(document.thresholds.blocking.globalMetricRegressionMaximum)
                .answerQualityPassRateMinimum(document.thresholds.scored.answerQualityPassRateMinimum)
                .defaultTrials(document.trialPolicy.defaultTrials)
                .standardMinimumPasses(document.trialPolicy.standardMinimumPasses)
                .highMinimumPasses(document.trialPolicy.highMinimumPasses)
                .invariantMinimumPasses(document.trialPolicy.invariantMinimumPasses)
                .pricingCurrency(document.pricing.currency)
                .pricingBudget(document.pricing.budget)
                .build();
    }

    private void requireRatio(BigDecimal value) {
        if (value == null || value.compareTo(ZERO) < 0 || value.compareTo(ONE) > 0) {
            throw new IllegalArgumentException("Invalid evaluation policy");
        }
    }

    private void requireNonNegative(BigDecimal value) {
        if (value == null || value.compareTo(ZERO) < 0) {
            throw new IllegalArgumentException("Invalid evaluation policy");
        }
    }

    public static final class PolicyDocument {
        public String policyId;
        public String mode;
        public String blockingProvider;
        public Thresholds thresholds;
        public TrialPolicy trialPolicy;
        public Pricing pricing;
    }

    public static final class Thresholds {
        public Blocking blocking;
        public Scored scored;
    }

    public static final class Blocking {
        public BigDecimal publicSubjectSmokeCoverageMinimum;
        public BigDecimal namedRouteTopOneMinimum;
        public BigDecimal deepSemanticRouteTopOneMinimum;
        public BigDecimal priorityDeepSemanticRouteTopOneMinimum;
        public BigDecimal retrievalHitAtFiveMinimum;
        public BigDecimal requiredClaimRecallMinimum;
        public BigDecimal providerTrialPassRateMinimum;
        public BigDecimal providerScenarioPassRateMinimum;
        public BigDecimal safetyBoundaryPassRateMinimum;
        public BigDecimal falseSufficientMaximum;
        public BigDecimal providerFailureRateMaximum;
        public long providerP95LatencyMaximumMs = -1L;
        public BigDecimal priorityMetricRegressionMaximum;
        public BigDecimal globalMetricRegressionMaximum;
    }

    public static final class Scored {
        public BigDecimal answerQualityPassRateMinimum;
    }

    public static final class TrialPolicy {
        public int defaultTrials;
        public int standardMinimumPasses;
        public int highMinimumPasses;
        public int invariantMinimumPasses;
    }

    public static final class Pricing {
        public String currency;
        public BigDecimal budget;
    }
}
