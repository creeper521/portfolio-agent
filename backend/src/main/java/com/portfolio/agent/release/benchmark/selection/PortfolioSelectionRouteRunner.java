package com.portfolio.agent.release.benchmark.selection;

/**
 * Evolution seam for measured route runners. Implementations must run against one
 * fixed public release/model and must never persist visitor query text.
 */
public interface PortfolioSelectionRouteRunner {
    BenchmarkRoute route();
    PortfolioSelectionObservation run(PortfolioSelectionBenchmarkCase benchmarkCase);
}
