package com.portfolio.agent.turn.capability.synthesis;

import com.portfolio.agent.turn.execution.TaskExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class CrossDomainAnchorSelectionTest {
    @Test void mismatchedAnchorIsNoResultWithoutSubstringGuessing() {
        TaskExecutionContext context = CrossDomainTestFixtures.context("并发控制");
        when(context.getDependencyResults()).thenReturn(List.of(
                CrossDomainTestFixtures.general("并发"), CrossDomainTestFixtures.portfolio()));
        assertThatThrownBy(() -> new CrossDomainTaskExecutor(new CrossDomainPresentationComposer())
                .execute(context)).hasMessageContaining("NO_RESULT:NO_SUPPORTED_RESULT");
    }
}
