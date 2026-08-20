package com.portfolio.agent.release.benchmark.selection;

public final class RouteBenchmarkMetrics {
    private final RouteAvailability availability;
    private final int evaluatedCaseCount;
    private final int unavailableCaseCount;
    private final double recallAt12;
    private final double hitAt1;
    private final double hitAt5;
    private final double meanReciprocalRank;
    private final double normalizedDiscountedCumulativeGain;
    private final double capabilityCoverage;
    private final double redundancy;
    private final double approvedEvidenceValidityRate;
    private final int falseSufficientCount;
    private final double falseSufficientRate;
    private final int unsupportedRecommendationCount;
    private final int crossReleaseMixCount;
    private final int invalidEvidenceCount;
    private final Long p50LatencyMilliseconds;
    private final Long p95LatencyMilliseconds;

    public RouteBenchmarkMetrics(
            RouteAvailability availability, int evaluatedCaseCount, int unavailableCaseCount,
            double recallAt12, double hitAt1, double hitAt5, double meanReciprocalRank,
            double normalizedDiscountedCumulativeGain, double capabilityCoverage, double redundancy,
            double approvedEvidenceValidityRate, int falseSufficientCount, double falseSufficientRate,
            int unsupportedRecommendationCount, int crossReleaseMixCount, int invalidEvidenceCount,
            Long p50LatencyMilliseconds, Long p95LatencyMilliseconds) {
        this.availability = availability;
        this.evaluatedCaseCount = evaluatedCaseCount;
        this.unavailableCaseCount = unavailableCaseCount;
        this.recallAt12 = recallAt12;
        this.hitAt1 = hitAt1;
        this.hitAt5 = hitAt5;
        this.meanReciprocalRank = meanReciprocalRank;
        this.normalizedDiscountedCumulativeGain = normalizedDiscountedCumulativeGain;
        this.capabilityCoverage = capabilityCoverage;
        this.redundancy = redundancy;
        this.approvedEvidenceValidityRate = approvedEvidenceValidityRate;
        this.falseSufficientCount = falseSufficientCount;
        this.falseSufficientRate = falseSufficientRate;
        this.unsupportedRecommendationCount = unsupportedRecommendationCount;
        this.crossReleaseMixCount = crossReleaseMixCount;
        this.invalidEvidenceCount = invalidEvidenceCount;
        this.p50LatencyMilliseconds = p50LatencyMilliseconds;
        this.p95LatencyMilliseconds = p95LatencyMilliseconds;
    }

    public static RouteBenchmarkMetrics unavailable(int caseCount) {
        return new RouteBenchmarkMetrics(RouteAvailability.UNAVAILABLE, 0, caseCount,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, 0, Double.NaN, 0, 0, 0, null, null);
    }

    public RouteAvailability getAvailability() { return availability; }
    public int getEvaluatedCaseCount() { return evaluatedCaseCount; }
    public int getUnavailableCaseCount() { return unavailableCaseCount; }
    public double getRecallAt12() { return recallAt12; }
    public double getHitAt1() { return hitAt1; }
    public double getHitAt5() { return hitAt5; }
    public double getMeanReciprocalRank() { return meanReciprocalRank; }
    public double getNormalizedDiscountedCumulativeGain() { return normalizedDiscountedCumulativeGain; }
    public double getNdcgAt12() { return normalizedDiscountedCumulativeGain; }
    public double getCapabilityCoverage() { return capabilityCoverage; }
    public double getRedundancy() { return redundancy; }
    public double getApprovedEvidenceValidityRate() { return approvedEvidenceValidityRate; }
    public int getFalseSufficientCount() { return falseSufficientCount; }
    public double getFalseSufficientRate() { return falseSufficientRate; }
    public int getUnsupportedRecommendationCount() { return unsupportedRecommendationCount; }
    public int getCrossReleaseMixCount() { return crossReleaseMixCount; }
    public int getInvalidEvidenceCount() { return invalidEvidenceCount; }
    public Long getP50LatencyMilliseconds() { return p50LatencyMilliseconds; }
    public Long getP95LatencyMilliseconds() { return p95LatencyMilliseconds; }
}
