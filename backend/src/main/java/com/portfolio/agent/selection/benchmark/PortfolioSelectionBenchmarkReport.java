package com.portfolio.agent.selection.benchmark;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class PortfolioSelectionBenchmarkReport {
    private final String releaseVersion;
    private final Map<BenchmarkRoute, RouteBenchmarkMetrics> routes;
    private final RouteBenchmarkMetrics overall;
    private final MigrationIntegrityResult migrationIntegrity;

    public PortfolioSelectionBenchmarkReport(
            String releaseVersion, Map<BenchmarkRoute, RouteBenchmarkMetrics> routes,
            RouteBenchmarkMetrics overall, MigrationIntegrityResult migrationIntegrity) {
        this.releaseVersion = Objects.requireNonNull(releaseVersion, "releaseVersion");
        this.routes = Collections.unmodifiableMap(new EnumMap<>(routes));
        this.overall = Objects.requireNonNull(overall, "overall");
        this.migrationIntegrity = migrationIntegrity;
    }

    public String getReleaseVersion() { return releaseVersion; }
    public Map<BenchmarkRoute, RouteBenchmarkMetrics> getRoutes() { return routes; }
    public RouteBenchmarkMetrics getOverall() { return overall; }
    public MigrationIntegrityResult getMigrationIntegrity() { return migrationIntegrity; }
}
