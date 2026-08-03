package com.portfolio.agent.selection.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.selection.domain.CandidateRetrievalResult;
import com.portfolio.agent.selection.domain.PortfolioSelectionStatus;
import com.portfolio.agent.selection.domain.PortfolioSubjectKind;
import com.portfolio.agent.selection.domain.RetrievalMode;
import com.portfolio.agent.selection.domain.SelectionCandidate;
import com.portfolio.agent.selection.domain.SelectionTarget;
import com.portfolio.agent.selection.service.ExhaustiveSelectionStrategy;
import com.portfolio.agent.selection.service.PortfolioSelectionService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SelectionBenchmarkSmokeTest {

    @Test
    void runsTheInternalSelectionAlgorithmWithoutTheHttpSurface() {
        List<SelectionCandidate> candidates = List.of(
                new SelectionCandidate(
                        "project-a", PortfolioSubjectKind.PROJECT, "BACKEND",
                        Set.of("JAVA"), 0.9d, 1.0d, 0.0d),
                new SelectionCandidate(
                        "case-b", PortfolioSubjectKind.CASE, "BACKEND",
                        Set.of("RAG"), 0.8d, 1.0d, 0.0d));
        PortfolioSelectionService service = new PortfolioSelectionService(
                (target, limit) -> new CandidateRetrievalResult(
                        "release-1", RetrievalMode.HYBRID, candidates),
                new ExhaustiveSelectionStrategy());

        com.portfolio.agent.selection.domain.PortfolioSelectionResult result = service.select(
                new SelectionTarget(
                        "BACKEND", "INTERVIEWER", Set.of("JAVA", "RAG"), null, 2));

        assertThat(result.getSelection().getStatus()).isEqualTo(PortfolioSelectionStatus.READY);
        assertThat(result.getSelection().getSubjectIds())
                .containsExactly("case-b", "project-a");
        assertThat(result.getSelection().getPolicyVersion()).isEqualTo("exhaustive-v1");
    }
}
