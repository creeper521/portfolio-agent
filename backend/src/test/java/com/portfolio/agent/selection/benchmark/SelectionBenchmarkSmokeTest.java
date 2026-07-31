package com.portfolio.agent.selection.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class SelectionBenchmarkSmokeTest {

    @Test
    void evaluatesTheFrozenPublicSuiteWithoutTheHttpSurface() throws Exception {
        PortfolioSelectionBenchmarkSuite suite;
        try (InputStream stream = getClass().getResourceAsStream(
                "/retrieval-benchmark/portfolio-selection-cases.json")) {
            suite = new ObjectMapper().readValue(stream, PortfolioSelectionBenchmarkSuite.class);
        }

        PortfolioSelectionBenchmarkReport report = new PortfolioSelectionBenchmarkEvaluator()
                .evaluate(suite.getReleaseVersion(), suite.getCases(), List.of(), null);

        assertThat(suite.getCases()).isNotEmpty();
        assertThat(report.getRoutes()).containsOnlyKeys(BenchmarkRoute.values());
        assertThat(report.getRoutes().values())
                .allMatch(metrics -> metrics.getAvailability() == RouteAvailability.UNAVAILABLE);
    }
}
