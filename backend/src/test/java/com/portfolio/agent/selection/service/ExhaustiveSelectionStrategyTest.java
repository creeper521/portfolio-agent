package com.portfolio.agent.selection.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.selection.domain.PortfolioSelection;
import com.portfolio.agent.selection.domain.PortfolioSelectionStatus;
import com.portfolio.agent.selection.domain.PortfolioSubjectKind;
import com.portfolio.agent.selection.domain.SelectionCandidate;
import com.portfolio.agent.selection.domain.SelectionTarget;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExhaustiveSelectionStrategyTest {

    private final ExhaustiveSelectionStrategy strategy = new ExhaustiveSelectionStrategy();

    @Test
    void selectsComplementaryEvidenceBackedCombinationInsteadOfThreeSimilarAssets() {
        SelectionTarget target = new SelectionTarget(
                "JAVA_BACKEND",
                "TECH_INTERVIEWER",
                Set.of("JAVA", "INCIDENT_ANALYSIS", "AGENT"),
                3);

        List<SelectionCandidate> candidates = List.of(
                candidate("PROJECT-01", PortfolioSubjectKind.PROJECT, "JAVA_BACKEND",
                        Set.of("JAVA", "DELIVERY"), 0.95, 0.90),
                candidate("CASE-02", PortfolioSubjectKind.CASE, "JAVA_BACKEND",
                        Set.of("JAVA", "INCIDENT_ANALYSIS"), 0.90, 0.95),
                candidate("PROJECT-04", PortfolioSubjectKind.PROJECT, "AGENT",
                        Set.of("AGENT", "RAG"), 0.70, 0.80),
                candidate("CASE-09", PortfolioSubjectKind.CASE, "JAVA_BACKEND",
                        Set.of("JAVA", "DELIVERY"), 0.94, 0.90));

        PortfolioSelection result = strategy.select(target, candidates);

        assertThat(result.getStatus()).isEqualTo(PortfolioSelectionStatus.READY);
        assertThat(result.getSubjectIds())
                .containsExactly("CASE-02", "PROJECT-01", "PROJECT-04");
        assertThat(result.getScore().getCapabilityCoverage()).isEqualTo(1.0);
        assertThat(result.getScore().getRedundancyPenalty()).isGreaterThanOrEqualTo(0.0);
        assertThat(result.getPolicyVersion()).isEqualTo("exhaustive-v1");
    }

    @Test
    void usesStableSubjectIdsToBreakEqualScoreTies() {
        SelectionTarget target = new SelectionTarget(
                null,
                "TECH_INTERVIEWER",
                Set.of("JAVA"),
                2);
        List<SelectionCandidate> candidates = List.of(
                candidate("CASE-Z", PortfolioSubjectKind.CASE, "JAVA_BACKEND",
                        Set.of("JAVA"), 0.8, 0.8),
                candidate("CASE-A", PortfolioSubjectKind.CASE, "JAVA_BACKEND",
                        Set.of("JAVA"), 0.8, 0.8),
                candidate("CASE-B", PortfolioSubjectKind.CASE, "JAVA_BACKEND",
                        Set.of("JAVA"), 0.8, 0.8));

        PortfolioSelection result = strategy.select(target, candidates);

        assertThat(result.getSubjectIds()).containsExactly("CASE-A", "CASE-B");
    }

    @Test
    void returnsFewerAssetsAndInsufficientStatusInsteadOfPadding() {
        SelectionTarget target = new SelectionTarget(
                "AGENT",
                "TECH_INTERVIEWER",
                Set.of("AGENT"),
                3);

        PortfolioSelection result = strategy.select(target, List.of(
                candidate("PROJECT-04", PortfolioSubjectKind.PROJECT, "AGENT",
                        Set.of("AGENT"), 0.9, 0.9)));

        assertThat(result.getStatus()).isEqualTo(PortfolioSelectionStatus.INSUFFICIENT);
        assertThat(result.getSubjectIds()).containsExactly("PROJECT-04");
    }

    @Test
    void returnsRequestedSizeButInsufficientWhenExplicitCapabilityIsUncovered() {
        SelectionTarget target = new SelectionTarget(
                "JAVA_BACKEND",
                "INTERVIEWER",
                Set.of("JAVA", "UNKNOWN"),
                2);

        PortfolioSelection result = strategy.select(target, List.of(
                candidate("PROJECT-01", PortfolioSubjectKind.PROJECT, "JAVA_BACKEND",
                        Set.of("JAVA"), 0.9, 0.9),
                candidate("CASE-02", PortfolioSubjectKind.CASE, "JAVA_BACKEND",
                        Set.of("JAVA"), 0.8, 0.9)));

        assertThat(result.getSubjectIds()).hasSize(2);
        assertThat(result.getStatus()).isEqualTo(PortfolioSelectionStatus.INSUFFICIENT);
    }

    private SelectionCandidate candidate(
            String subjectId,
            PortfolioSubjectKind kind,
            String careerTrack,
            Set<String> capabilities,
            double targetFit,
            double evidenceQuality) {
        return new SelectionCandidate(
                subjectId,
                kind,
                careerTrack,
                capabilities,
                targetFit,
                evidenceQuality,
                0.0);
    }
}
