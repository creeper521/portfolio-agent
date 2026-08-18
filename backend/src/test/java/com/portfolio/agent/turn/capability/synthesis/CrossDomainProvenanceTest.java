package com.portfolio.agent.turn.capability.synthesis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CrossDomainProvenanceTest {
    @Test void provenanceContainsOnlyActuallySelectedPortfolioSupport() {
        var execution = new CrossDomainTaskExecutor(new CrossDomainPresentationComposer())
                .execute(CrossDomainTestFixtures.context("并发控制"));
        assertThat(execution.getArtifact().getProvenance().getPublicSourceKeys())
                .containsExactly("E-01");
        CrossDomainSemanticResult result =
                (CrossDomainSemanticResult) execution.getArtifact().getSemanticResult();
        assertThat(result.getGeneralStatements())
                .allSatisfy(value -> assertThat(value.getText()).isNotBlank());
    }
}
