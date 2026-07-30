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

class TopKSelectionStrategyTest {

    private final TopKSelectionStrategy strategy = new TopKSelectionStrategy();

    @Test
    void ordersByRetrievalFitThenEvidenceQualityThenStableId() {
        SelectionTarget target = new SelectionTarget(
                null, "INTERVIEWER", Set.of("JAVA"), null, 2);

        PortfolioSelection result = strategy.select(target, List.of(
                candidate("PROJECT-Z", 0.8, 0.8),
                candidate("PROJECT-B", 0.9, 0.7),
                candidate("PROJECT-A", 0.9, 0.7)));

        assertThat(result.getSubjectIds()).containsExactly("PROJECT-A", "PROJECT-B");
        assertThat(result.getStatus()).isEqualTo(PortfolioSelectionStatus.READY);
        assertThat(result.getPolicyVersion()).isEqualTo("top-k-v1");
    }

    @Test
    void returnsAllEligibleCandidatesWithoutPaddingWhenRequestedSizeCannotBeMet() {
        SelectionTarget target = new SelectionTarget(
                null, "INTERVIEWER", Set.of("JAVA"), null, 3);

        PortfolioSelection result = strategy.select(target, List.of(candidate("PROJECT-A", 0.9, 0.9)));

        assertThat(result.getSubjectIds()).containsExactly("PROJECT-A");
        assertThat(result.getStatus()).isEqualTo(PortfolioSelectionStatus.INSUFFICIENT);
    }

    @Test
    void marksRequestedSizeAsInsufficientWhenExplicitCapabilityIsUncovered() {
        SelectionTarget target = new SelectionTarget(
                null, "INTERVIEWER", Set.of("JAVA", "RAG"), null, 2);

        PortfolioSelection result = strategy.select(target, List.of(
                candidate("PROJECT-A", 0.9, 0.9),
                candidate("PROJECT-B", 0.8, 0.9)));

        assertThat(result.getSubjectIds()).hasSize(2);
        assertThat(result.getStatus()).isEqualTo(PortfolioSelectionStatus.INSUFFICIENT);
    }

    private SelectionCandidate candidate(String id, double fit, double evidenceQuality) {
        return new SelectionCandidate(
                id,
                PortfolioSubjectKind.PROJECT,
                "Title " + id,
                "Summary " + id,
                "/projects/" + id.toLowerCase(),
                "JAVA_BACKEND",
                Set.of("JAVA"),
                List.of(),
                fit,
                evidenceQuality,
                0.0);
    }
}
