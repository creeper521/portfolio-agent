package com.portfolio.agent.release.benchmark;

public final class RetrievalBenchmarkMetrics {

    private final int positiveCount;
    private final double hitAt1;
    private final double hitAt5;
    private final double mrrAt5;
    private final int falseSufficientCount;

    public RetrievalBenchmarkMetrics(
            int positiveCount,
            double hitAt1,
            double hitAt5,
            double mrrAt5,
            int falseSufficientCount
    ) {
        this.positiveCount = positiveCount;
        this.hitAt1 = hitAt1;
        this.hitAt5 = hitAt5;
        this.mrrAt5 = mrrAt5;
        this.falseSufficientCount = falseSufficientCount;
    }

    public int getPositiveCount() { return positiveCount; }
    public double getHitAt1() { return hitAt1; }
    public double getHitAt5() { return hitAt5; }
    public double getMrrAt5() { return mrrAt5; }
    public int getFalseSufficientCount() { return falseSufficientCount; }
}
