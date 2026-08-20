package com.portfolio.agent.turn.capability.synthesis;

import com.portfolio.agent.turn.execution.TaskExecutionResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CrossDomainSupportIsolationTest {
    @Test void portfolioEvidenceNeverPropagatesToGeneralSection() {
        TaskExecutionResult execution = new CrossDomainTaskExecutor(new CrossDomainPresentationComposer())
                .execute(CrossDomainTestFixtures.context("并发控制"));
        CrossDomainPresentation presentation =
                (CrossDomainPresentation) execution.getArtifact().getPresentation();
        assertThat(presentation.getSections().get(0).sources()).isEmpty();
        assertThat(presentation.getSections().get(1).sources())
                .extracting(value -> value.getReferenceKey()).containsExactly("E-01");
    }
}
