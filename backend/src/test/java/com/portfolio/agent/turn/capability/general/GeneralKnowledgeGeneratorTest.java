package com.portfolio.agent.turn.capability.general;

import org.junit.jupiter.api.Test;
import com.portfolio.agent.infrastructure.model.ModelExecutionSnapshot;
import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.infrastructure.model.SelectedModelFailureException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneralKnowledgeGeneratorTest {
    @Test void callsProviderExactlyOnceAndReturnsStrictResult() {
        AtomicInteger calls = new AtomicInteger();
        GeneralKnowledgeGenerator generator = GeneralTestFixtures.generator((request, modelExecution) -> {
            calls.incrementAndGet();
            return GeneralTestFixtures.VALID_EXPLANATION;
        });
        assertThat(generator.generate(GeneralTestFixtures.explanation()).getTopic()).isEqualTo("并发控制");
        assertThat(calls).hasValue(1);
    }

    @Test void invalidProviderDraftIsAClosedFailure() {
        AtomicInteger calls = new AtomicInteger();
        GeneralKnowledgeGenerator generator = GeneralTestFixtures.generator((request, modelExecution) -> {
            calls.incrementAndGet();
            return "{}";
        });
        assertThatThrownBy(() -> generator.generate(GeneralTestFixtures.explanation()))
                .isInstanceOf(GeneralKnowledgeUnavailableException.class);
        assertThat(calls).as("schema rejection must not trigger repair").hasValue(1);
    }

    @Test void invalidSelectedModelDraftKeepsTheSelectedModelFailureCode() {
        GeneralKnowledgeGenerator generator = GeneralTestFixtures.generator(
                (request, modelExecution) -> "{}");
        ResolvedModelExecution modelExecution =
                org.mockito.Mockito.mock(ResolvedModelExecution.class);
        ModelExecutionSnapshot snapshot =
                org.mockito.Mockito.mock(ModelExecutionSnapshot.class);
        org.mockito.Mockito.when(modelExecution.getSnapshot()).thenReturn(snapshot);
        org.mockito.Mockito.when(snapshot.getKind()).thenReturn(
                ModelExecutionSnapshot.Kind.MODEL);

        assertThatThrownBy(() -> generator.generate(
                GeneralTestFixtures.explanation(), modelExecution))
                .isInstanceOf(SelectedModelFailureException.class)
                .extracting(failure ->
                        ((SelectedModelFailureException) failure).getCode())
                .isEqualTo(SelectedModelFailureException.Code
                        .SELECTED_MODEL_INVALID_RESPONSE);
    }
}
