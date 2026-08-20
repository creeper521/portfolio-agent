package com.portfolio.agent.turn.capability.synthesis;

import com.portfolio.agent.turn.execution.TaskOutcome;
import com.portfolio.agent.turn.execution.TaskExecutionResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CrossDomainTaskExecutorTest {
    @Test void consumesExactlyOneGeneralAndOnePortfolioSemanticResult() {
        TaskExecutionResult execution = new CrossDomainTaskExecutor(new CrossDomainPresentationComposer())
                .execute(CrossDomainTestFixtures.context("并发控制"));
        assertThat(execution.getFulfillment()).isEqualTo(TaskOutcome.Fulfillment.FULL);
        assertThat(execution.getArtifact().getSemanticResult())
                .isInstanceOf(CrossDomainSemanticResult.class);
        CrossDomainSemanticResult result =
                (CrossDomainSemanticResult) execution.getArtifact().getSemanticResult();
        assertThat(result.getGeneralStatements()).hasSize(2);
        assertThat(result.getPortfolioStatements()).hasSize(1);
    }
}
