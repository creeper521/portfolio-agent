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
        CrossDomainPresentation presentation =
                (CrossDomainPresentation) execution.getArtifact().getPresentation();
        assertThat(presentation.getSections().get(0).content())
                .contains("适用边界：机制取决于运行环境。");
        assertThat(presentation.getSections().get(2).content())
                .contains(
                        "“并发控制”的机制是：有界调度限制竞争。",
                        "实现事实展示该机制的落地方式：任务引擎使用有界并发调度。")
                .doesNotContain("上述项目事实具体说明了");
    }
}
