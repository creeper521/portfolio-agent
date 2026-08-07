package com.portfolio.agent.evaluation.domain;

import java.math.BigDecimal;
import java.util.Objects;

public final class EvalPolicy {

    private final String policyId;
    private final String mode;
    private final String blockingProvider;

    private final BigDecimal publicSubjectSmokeCoverageMinimum;
    private final BigDecimal namedRouteTopOneMinimum;
    private final BigDecimal deepSemanticRouteTopOneMinimum;
    private final BigDecimal priorityDeepSemanticRouteTopOneMinimum;
    private final BigDecimal retrievalHitAtFiveMinimum;
    private final BigDecimal requiredClaimRecallMinimum;
    private final BigDecimal providerTrialPassRateMinimum;
    private final BigDecimal providerScenarioPassRateMinimum;
    private final BigDecimal safetyBoundaryPassRateMinimum;
    private final BigDecimal falseSufficientMaximum;
    private final BigDecimal providerFailureRateMaximum;
    private final long providerP95LatencyMaximumMs;
    private final BigDecimal priorityMetricRegressionMaximum;
    private final BigDecimal globalMetricRegressionMaximum;

    private final BigDecimal answerQualityPassRateMinimum;

    private final int defaultTrials;
    private final int standardMinimumPasses;
    private final int highMinimumPasses;
    private final int invariantMinimumPasses;

    private final String pricingCurrency;
    private final BigDecimal pricingBudget;

    private EvalPolicy(Builder builder) {
        this.policyId = builder.policyId;
        this.mode = builder.mode;
        this.blockingProvider = builder.blockingProvider;
        this.publicSubjectSmokeCoverageMinimum = builder.publicSubjectSmokeCoverageMinimum;
        this.namedRouteTopOneMinimum = builder.namedRouteTopOneMinimum;
        this.deepSemanticRouteTopOneMinimum = builder.deepSemanticRouteTopOneMinimum;
        this.priorityDeepSemanticRouteTopOneMinimum = builder.priorityDeepSemanticRouteTopOneMinimum;
        this.retrievalHitAtFiveMinimum = builder.retrievalHitAtFiveMinimum;
        this.requiredClaimRecallMinimum = builder.requiredClaimRecallMinimum;
        this.providerTrialPassRateMinimum = builder.providerTrialPassRateMinimum;
        this.providerScenarioPassRateMinimum = builder.providerScenarioPassRateMinimum;
        this.safetyBoundaryPassRateMinimum = builder.safetyBoundaryPassRateMinimum;
        this.falseSufficientMaximum = builder.falseSufficientMaximum;
        this.providerFailureRateMaximum = builder.providerFailureRateMaximum;
        this.providerP95LatencyMaximumMs = builder.providerP95LatencyMaximumMs;
        this.priorityMetricRegressionMaximum = builder.priorityMetricRegressionMaximum;
        this.globalMetricRegressionMaximum = builder.globalMetricRegressionMaximum;
        this.answerQualityPassRateMinimum = builder.answerQualityPassRateMinimum;
        this.defaultTrials = builder.defaultTrials;
        this.standardMinimumPasses = builder.standardMinimumPasses;
        this.highMinimumPasses = builder.highMinimumPasses;
        this.invariantMinimumPasses = builder.invariantMinimumPasses;
        this.pricingCurrency = builder.pricingCurrency;
        this.pricingBudget = builder.pricingBudget;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getPolicyId() { return policyId; }
    public String getMode() { return mode; }
    public String getBlockingProvider() { return blockingProvider; }
    public BigDecimal getPublicSubjectSmokeCoverageMinimum() { return publicSubjectSmokeCoverageMinimum; }
    public BigDecimal getNamedRouteTopOneMinimum() { return namedRouteTopOneMinimum; }
    public BigDecimal getDeepSemanticRouteTopOneMinimum() { return deepSemanticRouteTopOneMinimum; }
    public BigDecimal getPriorityDeepSemanticRouteTopOneMinimum() { return priorityDeepSemanticRouteTopOneMinimum; }
    public BigDecimal getRetrievalHitAtFiveMinimum() { return retrievalHitAtFiveMinimum; }
    public BigDecimal getRequiredClaimRecallMinimum() { return requiredClaimRecallMinimum; }
    public BigDecimal getProviderTrialPassRateMinimum() { return providerTrialPassRateMinimum; }
    public BigDecimal getProviderScenarioPassRateMinimum() { return providerScenarioPassRateMinimum; }
    public BigDecimal getSafetyBoundaryPassRateMinimum() { return safetyBoundaryPassRateMinimum; }
    public BigDecimal getFalseSufficientMaximum() { return falseSufficientMaximum; }
    public BigDecimal getProviderFailureRateMaximum() { return providerFailureRateMaximum; }
    public long getProviderP95LatencyMaximumMs() { return providerP95LatencyMaximumMs; }
    public BigDecimal getPriorityMetricRegressionMaximum() { return priorityMetricRegressionMaximum; }
    public BigDecimal getGlobalMetricRegressionMaximum() { return globalMetricRegressionMaximum; }
    public BigDecimal getAnswerQualityPassRateMinimum() { return answerQualityPassRateMinimum; }
    public int getDefaultTrials() { return defaultTrials; }
    public int getStandardMinimumPasses() { return standardMinimumPasses; }
    public int getHighMinimumPasses() { return highMinimumPasses; }
    public int getInvariantMinimumPasses() { return invariantMinimumPasses; }
    public String getPricingCurrency() { return pricingCurrency; }
    public BigDecimal getPricingBudget() { return pricingBudget; }

    public static final class Builder {
        private String policyId;
        private String mode;
        private String blockingProvider;
        private BigDecimal publicSubjectSmokeCoverageMinimum;
        private BigDecimal namedRouteTopOneMinimum;
        private BigDecimal deepSemanticRouteTopOneMinimum;
        private BigDecimal priorityDeepSemanticRouteTopOneMinimum;
        private BigDecimal retrievalHitAtFiveMinimum;
        private BigDecimal requiredClaimRecallMinimum;
        private BigDecimal providerTrialPassRateMinimum;
        private BigDecimal providerScenarioPassRateMinimum;
        private BigDecimal safetyBoundaryPassRateMinimum;
        private BigDecimal falseSufficientMaximum;
        private BigDecimal providerFailureRateMaximum;
        private long providerP95LatencyMaximumMs = -1L;
        private BigDecimal priorityMetricRegressionMaximum;
        private BigDecimal globalMetricRegressionMaximum;
        private BigDecimal answerQualityPassRateMinimum;
        private int defaultTrials;
        private int standardMinimumPasses;
        private int highMinimumPasses;
        private int invariantMinimumPasses;
        private String pricingCurrency;
        private BigDecimal pricingBudget;

        public Builder policyId(String value) { this.policyId = Objects.requireNonNull(value, "policyId"); return this; }
        public Builder mode(String value) { this.mode = Objects.requireNonNull(value, "mode"); return this; }
        public Builder blockingProvider(String value) { this.blockingProvider = Objects.requireNonNull(value, "blockingProvider"); return this; }
        public Builder publicSubjectSmokeCoverageMinimum(BigDecimal value) { this.publicSubjectSmokeCoverageMinimum = value; return this; }
        public Builder namedRouteTopOneMinimum(BigDecimal value) { this.namedRouteTopOneMinimum = value; return this; }
        public Builder deepSemanticRouteTopOneMinimum(BigDecimal value) { this.deepSemanticRouteTopOneMinimum = value; return this; }
        public Builder priorityDeepSemanticRouteTopOneMinimum(BigDecimal value) { this.priorityDeepSemanticRouteTopOneMinimum = value; return this; }
        public Builder retrievalHitAtFiveMinimum(BigDecimal value) { this.retrievalHitAtFiveMinimum = value; return this; }
        public Builder requiredClaimRecallMinimum(BigDecimal value) { this.requiredClaimRecallMinimum = value; return this; }
        public Builder providerTrialPassRateMinimum(BigDecimal value) { this.providerTrialPassRateMinimum = value; return this; }
        public Builder providerScenarioPassRateMinimum(BigDecimal value) { this.providerScenarioPassRateMinimum = value; return this; }
        public Builder safetyBoundaryPassRateMinimum(BigDecimal value) { this.safetyBoundaryPassRateMinimum = value; return this; }
        public Builder falseSufficientMaximum(BigDecimal value) { this.falseSufficientMaximum = value; return this; }
        public Builder providerFailureRateMaximum(BigDecimal value) { this.providerFailureRateMaximum = value; return this; }
        public Builder providerP95LatencyMaximumMs(long value) { this.providerP95LatencyMaximumMs = value; return this; }
        public Builder priorityMetricRegressionMaximum(BigDecimal value) { this.priorityMetricRegressionMaximum = value; return this; }
        public Builder globalMetricRegressionMaximum(BigDecimal value) { this.globalMetricRegressionMaximum = value; return this; }
        public Builder answerQualityPassRateMinimum(BigDecimal value) { this.answerQualityPassRateMinimum = value; return this; }
        public Builder defaultTrials(int value) { this.defaultTrials = value; return this; }
        public Builder standardMinimumPasses(int value) { this.standardMinimumPasses = value; return this; }
        public Builder highMinimumPasses(int value) { this.highMinimumPasses = value; return this; }
        public Builder invariantMinimumPasses(int value) { this.invariantMinimumPasses = value; return this; }
        public Builder pricingCurrency(String value) { this.pricingCurrency = Objects.requireNonNull(value, "pricingCurrency"); return this; }
        public Builder pricingBudget(BigDecimal value) { this.pricingBudget = value; return this; }

        public EvalPolicy build() {
            return new EvalPolicy(this);
        }
    }
}
