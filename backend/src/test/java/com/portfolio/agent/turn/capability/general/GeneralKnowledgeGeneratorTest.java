package com.portfolio.agent.turn.capability.general;

import org.junit.jupiter.api.Test;
import com.portfolio.agent.infrastructure.model.ModelExecutionSnapshot;
import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.infrastructure.model.SelectedModelFailureException;
import com.portfolio.agent.infrastructure.model.structured.StructuredModelTestFixtures;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.structured.StructuredContractRef;
import com.portfolio.agent.infrastructure.model.structured.StructurallyValidatedOutput;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneralKnowledgeGeneratorTest {
    @Test
    void rejectsAValidatedTreeCarriedUnderTheWrongOperationContract() {
        StructuredContractRef wrongRef = new StructuredContractRef(
                ModelOperation.TURN_INTERPRETATION, "goal.proposal.v5");
        StructurallyValidatedOutput wrongContract =
                StructuredModelTestFixtures.contracts().validate(
                        wrongRef,
                        "{\"kind\":\"CONVERSATIONAL\","
                                + "\"message\":\"请说明目标\"}");
        GeneralKnowledgeGenerator generator = GeneralTestFixtures.generator(
                (request, modelExecution) -> wrongContract);

        assertThatThrownBy(() -> generator.generate(GeneralTestFixtures.explanation()))
                .isInstanceOf(GeneralKnowledgeUnavailableException.class);
    }

    @Test void callsProviderExactlyOnceAndReturnsStrictResult() {
        AtomicInteger calls = new AtomicInteger();
        GeneralKnowledgeGenerator generator = GeneralTestFixtures.generator((request, modelExecution) -> {
            calls.incrementAndGet();
            return StructuredModelTestFixtures.validatedGeneral(
                    GeneralTestFixtures.VALID_EXPLANATION);
        });
        assertThat(generator.generate(GeneralTestFixtures.explanation()).getTopic()).isEqualTo("并发控制");
        assertThat(calls).hasValue(1);
    }

    @Test void invalidProviderDraftIsAClosedFailure() {
        AtomicInteger calls = new AtomicInteger();
        GeneralKnowledgeGenerator generator = GeneralTestFixtures.generator((request, modelExecution) -> {
            calls.incrementAndGet();
            return StructuredModelTestFixtures.validatedGeneral(
                    semanticallyInvalidGeneral());
        });
        assertThatThrownBy(() -> generator.generate(GeneralTestFixtures.explanation()))
                .isInstanceOf(GeneralKnowledgeUnavailableException.class);
        assertThat(calls).as("schema rejection must not trigger repair").hasValue(1);
    }

    @Test void invalidSelectedModelDraftKeepsTheSelectedModelFailureCode() {
        GeneralKnowledgeGenerator generator = GeneralTestFixtures.generator(
                (request, modelExecution) ->
                        StructuredModelTestFixtures.validatedGeneral(
                                semanticallyInvalidGeneral()));
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

    private static String semanticallyInvalidGeneral() {
        return GeneralTestFixtures.VALID_EXPLANATION.replace(
                "\"topic\":\"并发控制\"", "\"topic\":\"其他主题\"");
    }
}
