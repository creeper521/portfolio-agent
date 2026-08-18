package com.portfolio.agent.turn.capability.portfolio.semantic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioCoverageTest {
    @Test
    void semanticResultCoverageHasNoThirdOrDegradedAxis() {
        assertThat(PortfolioSemanticResult.Coverage.values()).containsExactly(
                PortfolioSemanticResult.Coverage.FULL,
                PortfolioSemanticResult.Coverage.PARTIAL);
        assertThat(java.util.Arrays.stream(PortfolioSemanticResult.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .doesNotContain("degraded", "resolution", "executionStatus", "evidenceState");
    }
}
