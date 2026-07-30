package com.portfolio.agent.selection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.agent.selection.domain.PortfolioSelectionStatus;
import com.portfolio.agent.selection.domain.SelectionTarget;
import com.portfolio.agent.selection.gateway.CandidateRetrievalException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PortfolioSelectionServiceTest {

    @Test
    void mapsOnlyDeclaredRetrievalInfrastructureFailureToTemporaryUnavailable() {
        PortfolioSelectionService service = new PortfolioSelectionService(
                (target, limit) -> {
                    throw new CandidateRetrievalException("database unavailable");
                },
                new ExhaustiveSelectionStrategy());

        assertThat(service.select(target()).getSelection().getStatus())
                .isEqualTo(PortfolioSelectionStatus.TEMPORARILY_UNAVAILABLE);
    }

    @Test
    void doesNotHideStrategyProgrammingDefectsAsInfrastructureDegradation() {
        SelectionStrategy defective = new SelectionStrategy() {
            @Override
            public com.portfolio.agent.selection.domain.PortfolioSelection select(
                    SelectionTarget target,
                    java.util.List<com.portfolio.agent.selection.domain.SelectionCandidate> candidates) {
                throw new IllegalStateException("strategy defect");
            }

            @Override
            public String policyVersion() {
                return "defective-v1";
            }
        };
        PortfolioSelectionService service = new PortfolioSelectionService(
                (target, limit) -> new com.portfolio.agent.selection.domain.CandidateRetrievalResult(
                        "release", com.portfolio.agent.selection.domain.RetrievalMode.HYBRID, java.util.List.of()),
                defective);

        assertThatThrownBy(() -> service.select(target()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("strategy defect");
    }

    private SelectionTarget target() {
        return new SelectionTarget(null, "INTERVIEWER", Set.of(), null, 3);
    }
}
