package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AgentExecutionSnapshot;
import com.portfolio.agent.answer.domain.AnswerPlan;
import com.portfolio.agent.answer.domain.GeneratedAnswer;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.domain.ModelAnswerDraft;
import com.portfolio.agent.answer.domain.ModelExpressionFailureCode;
import com.portfolio.agent.answer.domain.ModelExpressionRequest;
import com.portfolio.agent.answer.domain.ModelExpressionResult;
import com.portfolio.agent.answer.engine.AnswerEngine;
import com.portfolio.agent.answer.gateway.ModelExpressionPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ModelAnswerCoordinatorTest {

    @Test
    void disabledPolicyUsesDeterministicOutputWithoutCallingTheProvider() {
        Fixture fixture = fixture(false);

        ModelAnswerOutcome outcome = fixture.coordinator().generate(
                fixture.snapshot(), fixture.plan());

        assertThat(outcome.getGenerationMode()).isEqualTo(GenerationMode.DETERMINISTIC);
        assertThat(outcome.getAnswer()).isSameAs(fixture.deterministicAnswer());
        assertThat(outcome.getFailureCode()).isNull();
        verifyNoInteractions(fixture.modelPort(), fixture.validator());
    }

    @Test
    void validCompleteDraftBecomesModelOutput() {
        Fixture fixture = fixture(true);
        ModelAnswerDraft draft = new ModelAnswerDraft(
                "Model title", "Model summary", List.of());
        GeneratedAnswer modelAnswer = new GeneratedAnswer(
                "Model title", "Model summary", List.of());
        when(fixture.modelPort().express(any(ModelExpressionRequest.class)))
                .thenReturn(ModelExpressionResult.success(draft));
        when(fixture.validator().validate(fixture.plan(), draft))
                .thenReturn(AnswerValidationResult.accepted(modelAnswer));

        ModelAnswerOutcome outcome = fixture.coordinator().generate(
                fixture.snapshot(), fixture.plan());

        assertThat(outcome.getGenerationMode()).isEqualTo(GenerationMode.MODEL);
        assertThat(outcome.getAnswer()).isSameAs(modelAnswer);
        verify(fixture.answerEngine(), never()).answer(any(AnswerPlan.class));
    }

    @Test
    void providerFailureDiscardsTheAttemptAndUsesSamePlanFallback() {
        Fixture fixture = fixture(true);
        when(fixture.modelPort().express(any(ModelExpressionRequest.class)))
                .thenReturn(ModelExpressionResult.failure(
                        ModelExpressionFailureCode.PROVIDER_ERROR));

        ModelAnswerOutcome outcome = fixture.coordinator().generate(
                fixture.snapshot(), fixture.plan());

        assertThat(outcome.getGenerationMode()).isEqualTo(GenerationMode.FALLBACK);
        assertThat(outcome.getAnswer()).isSameAs(fixture.deterministicAnswer());
        assertThat(outcome.getFailureCode())
                .isEqualTo(ModelExpressionFailureCode.PROVIDER_ERROR);
        verify(fixture.answerEngine()).answer(fixture.plan());
    }

    @Test
    void rejectedDraftIsNeverPartiallyMergedIntoFallback() {
        Fixture fixture = fixture(true);
        ModelAnswerDraft draft = new ModelAnswerDraft(
                "Untrusted title", "Untrusted summary", List.of());
        when(fixture.modelPort().express(any(ModelExpressionRequest.class)))
                .thenReturn(ModelExpressionResult.success(draft));
        when(fixture.validator().validate(fixture.plan(), draft))
                .thenReturn(AnswerValidationResult.rejected(
                        AnswerValidationFailureCode.INVALID_REFERENCE));

        ModelAnswerOutcome outcome = fixture.coordinator().generate(
                fixture.snapshot(), fixture.plan());

        assertThat(outcome.getGenerationMode()).isEqualTo(GenerationMode.FALLBACK);
        assertThat(outcome.getAnswer()).isSameAs(fixture.deterministicAnswer());
        assertThat(outcome.getAnswer().getTitle()).doesNotContain("Untrusted");
        assertThat(outcome.getFailureCode())
                .isEqualTo(ModelExpressionFailureCode.DRAFT_REJECTED);
    }

    @Test
    void validatorFailureIsFailClosedToTheSamePlanFallback() {
        Fixture fixture = fixture(true);
        ModelAnswerDraft draft = new ModelAnswerDraft(
                "Untrusted title", "Untrusted summary", List.of());
        when(fixture.modelPort().express(any(ModelExpressionRequest.class)))
                .thenReturn(ModelExpressionResult.success(draft));
        when(fixture.validator().validate(fixture.plan(), draft))
                .thenThrow(new IllegalStateException("validator internal detail"));

        ModelAnswerOutcome outcome = fixture.coordinator().generate(
                fixture.snapshot(), fixture.plan());

        assertThat(outcome.getGenerationMode()).isEqualTo(GenerationMode.FALLBACK);
        assertThat(outcome.getAnswer()).isSameAs(fixture.deterministicAnswer());
        assertThat(outcome.getFailureCode())
                .isEqualTo(ModelExpressionFailureCode.DRAFT_REJECTED);
    }

    private Fixture fixture(boolean enabled) {
        AnswerEngine answerEngine = mock(AnswerEngine.class);
        ModelExpressionPort modelPort = mock(ModelExpressionPort.class);
        AnswerOutputValidator validator = mock(AnswerOutputValidator.class);
        AnswerPlan plan = mock(AnswerPlan.class);
        AgentExecutionSnapshot snapshot = mock(AgentExecutionSnapshot.class);
        GeneratedAnswer deterministicAnswer = new GeneratedAnswer(
                "Deterministic title", "Deterministic summary", List.of());
        when(snapshot.isModelExpressionEnabled()).thenReturn(enabled);
        when(snapshot.getAnswerSchemaVersion()).thenReturn("c1.answer.v1");
        when(answerEngine.answer(plan)).thenReturn(deterministicAnswer);
        ModelAnswerCoordinator coordinator = new ModelAnswerCoordinator(
                answerEngine, modelPort, validator);
        return new Fixture(
                coordinator,
                answerEngine,
                modelPort,
                validator,
                plan,
                snapshot,
                deterministicAnswer
        );
    }

    private static final class Fixture {
        private final ModelAnswerCoordinator coordinator;
        private final AnswerEngine answerEngine;
        private final ModelExpressionPort modelPort;
        private final AnswerOutputValidator validator;
        private final AnswerPlan plan;
        private final AgentExecutionSnapshot snapshot;
        private final GeneratedAnswer deterministicAnswer;

        private Fixture(
                ModelAnswerCoordinator coordinator,
                AnswerEngine answerEngine,
                ModelExpressionPort modelPort,
                AnswerOutputValidator validator,
                AnswerPlan plan,
                AgentExecutionSnapshot snapshot,
                GeneratedAnswer deterministicAnswer
        ) {
            this.coordinator = coordinator;
            this.answerEngine = answerEngine;
            this.modelPort = modelPort;
            this.validator = validator;
            this.plan = plan;
            this.snapshot = snapshot;
            this.deterministicAnswer = deterministicAnswer;
        }

        private ModelAnswerCoordinator coordinator() { return coordinator; }
        private AnswerEngine answerEngine() { return answerEngine; }
        private ModelExpressionPort modelPort() { return modelPort; }
        private AnswerOutputValidator validator() { return validator; }
        private AnswerPlan plan() { return plan; }
        private AgentExecutionSnapshot snapshot() { return snapshot; }
        private GeneratedAnswer deterministicAnswer() { return deterministicAnswer; }
    }
}
