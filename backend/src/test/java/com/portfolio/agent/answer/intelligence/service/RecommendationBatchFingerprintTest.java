package com.portfolio.agent.answer.intelligence.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecommendationBatchFingerprintTest {

    private final RecommendationBatchFingerprint fingerprint = new RecommendationBatchFingerprint();

    @Test
    void isStableForCapabilityOrderButSensitiveToSelectedPortfolioOrderAndConditions() {
        String stable = fingerprint.calculate(
                "public-2026-07-31",
                new PortfolioConditions("JAVA_BACKEND", "INTERVIEWER", Set.of("RAG", "JAVA"), "first goal", 2),
                List.of("PROJECT-A", "CASE-B"));
        String reorderedCapabilities = fingerprint.calculate(
                "public-2026-07-31",
                new PortfolioConditions("JAVA_BACKEND", "INTERVIEWER", Set.of("JAVA", "RAG"), "other goal", 2),
                List.of("PROJECT-A", "CASE-B"));
        String reorderedSelections = fingerprint.calculate(
                "public-2026-07-31",
                new PortfolioConditions("JAVA_BACKEND", "INTERVIEWER", Set.of("JAVA", "RAG"), null, 2),
                List.of("CASE-B", "PROJECT-A"));
        String changedVersion = fingerprint.calculate(
                "public-2026-08-01",
                new PortfolioConditions("JAVA_BACKEND", "INTERVIEWER", Set.of("JAVA", "RAG"), null, 2),
                List.of("PROJECT-A", "CASE-B"));

        assertThat(stable).matches("rec_[0-9a-f]{64}");
        assertThat(reorderedCapabilities).isEqualTo(stable);
        assertThat(reorderedSelections).isNotEqualTo(stable);
        assertThat(changedVersion).isNotEqualTo(stable);
    }
}
