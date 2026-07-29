package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AgentExecutionSnapshot;
import com.portfolio.agent.answer.domain.AnswerPlan;
import com.portfolio.agent.answer.domain.GeneratedAnswer;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.domain.DurationBucket;
import com.portfolio.agent.answer.domain.ModelAnswerDraft;
import com.portfolio.agent.answer.domain.ModelExpressionFailureCode;
import com.portfolio.agent.answer.domain.ModelExpressionRequest;
import com.portfolio.agent.answer.domain.ModelExpressionResult;
import com.portfolio.agent.answer.engine.AnswerEngine;
import com.portfolio.agent.answer.gateway.ModelExpressionPort;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.DiagnosticLevel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
        assertThat(fixture.events()).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("answer.validation.completed");
            assertThat(event.getLevel()).isEqualTo(DiagnosticLevel.DEBUG);
            assertThat(event.getFields()).containsOnly(
                    org.assertj.core.api.Assertions.entry("validation.accepted", true),
                    org.assertj.core.api.Assertions.entry("failure.code", "NONE"),
                    org.assertj.core.api.Assertions.entry(
                            "duration.bucket",
                            event.getFields().get("duration.bucket")));
            assertDurationBucket(event.getFields().get("duration.bucket"));
        });
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
        assertThat(fixture.events()).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("answer.fallback.selected");
            assertThat(event.getLevel()).isEqualTo(DiagnosticLevel.WARN);
            assertThat(event.getFields()).containsOnly(
                    org.assertj.core.api.Assertions.entry(
                            "fallback.trigger", "PROVIDER_FAILURE"),
                    org.assertj.core.api.Assertions.entry(
                            "failure.code", "PROVIDER_CONNECTION_FAILED"));
        });
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
        assertThat(fixture.events()).hasSize(2);
        assertThat(fixture.events().get(0)).satisfies(event -> {
            assertThat(event.getName()).isEqualTo("answer.validation.completed");
            assertThat(event.getLevel()).isEqualTo(DiagnosticLevel.WARN);
            assertThat(event.getFields())
                    .containsOnly(
                            org.assertj.core.api.Assertions.entry(
                                    "validation.accepted", false),
                            org.assertj.core.api.Assertions.entry(
                                    "failure.code", "INVALID_REFERENCE"),
                            org.assertj.core.api.Assertions.entry(
                                    "duration.bucket",
                                    event.getFields().get("duration.bucket")));
            assertDurationBucket(event.getFields().get("duration.bucket"));
        });
        assertThat(fixture.events().get(1)).satisfies(event -> {
            assertThat(event.getName()).isEqualTo("answer.fallback.selected");
            assertThat(event.getFields()).containsOnly(
                    org.assertj.core.api.Assertions.entry(
                            "fallback.trigger", "VALIDATION_REJECTED"),
                    org.assertj.core.api.Assertions.entry(
                            "failure.code", "PROVIDER_DRAFT_REJECTED"));
        });
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
        assertThat(fixture.events()).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("answer.fallback.selected");
            assertThat(event.getLevel()).isEqualTo(DiagnosticLevel.WARN);
            assertThat(event.getFields())
                    .containsEntry(
                            "fallback.trigger",
                            "VALIDATION_EXCEPTION")
                    .containsEntry(
                            "failure.code",
                            "PROVIDER_DRAFT_REJECTED")
                    .containsOnlyKeys("fallback.trigger", "failure.code");
        });
    }

    @Test
    void diagnosticFailureDoesNotChangeProviderFallbackOutcome() {
        DiagnosticEventPublisher throwingPublisher = event -> {
            throw new IllegalStateException("diagnostics unavailable");
        };
        Fixture fixture = fixture(true, throwingPublisher, List.of());
        when(fixture.modelPort().express(any(ModelExpressionRequest.class)))
                .thenReturn(ModelExpressionResult.failure(
                        ModelExpressionFailureCode.TIMEOUT));

        ModelAnswerOutcome outcome = fixture.coordinator().generate(
                fixture.snapshot(), fixture.plan());

        assertThat(outcome.getGenerationMode()).isEqualTo(GenerationMode.FALLBACK);
        assertThat(outcome.getAnswer()).isSameAs(fixture.deterministicAnswer());
        assertThat(outcome.getFailureCode()).isEqualTo(ModelExpressionFailureCode.TIMEOUT);
    }

    private Fixture fixture(boolean enabled) {
        List<DiagnosticEvent> events = new ArrayList<>();
        return fixture(enabled, events::add, events);
    }

    private void assertDurationBucket(Object value) {
        assertThat(value).isInstanceOf(String.class);
        assertThat(java.util.Arrays.stream(DurationBucket.values())
                .map(DurationBucket::name))
                .contains((String) value);
    }

    private Fixture fixture(
            boolean enabled,
            DiagnosticEventPublisher diagnosticEventPublisher,
            List<DiagnosticEvent> events
    ) {
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
                answerEngine, modelPort, validator, diagnosticEventPublisher);
        return new Fixture(
                coordinator,
                answerEngine,
                modelPort,
                validator,
                plan,
                snapshot,
                deterministicAnswer,
                events
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
        private final List<DiagnosticEvent> events;

        private Fixture(
                ModelAnswerCoordinator coordinator,
                AnswerEngine answerEngine,
                ModelExpressionPort modelPort,
                AnswerOutputValidator validator,
                AnswerPlan plan,
                AgentExecutionSnapshot snapshot,
                GeneratedAnswer deterministicAnswer,
                List<DiagnosticEvent> events
        ) {
            this.coordinator = coordinator;
            this.answerEngine = answerEngine;
            this.modelPort = modelPort;
            this.validator = validator;
            this.plan = plan;
            this.snapshot = snapshot;
            this.deterministicAnswer = deterministicAnswer;
            this.events = events;
        }

        private ModelAnswerCoordinator coordinator() { return coordinator; }
        private AnswerEngine answerEngine() { return answerEngine; }
        private ModelExpressionPort modelPort() { return modelPort; }
        private AnswerOutputValidator validator() { return validator; }
        private AnswerPlan plan() { return plan; }
        private AgentExecutionSnapshot snapshot() { return snapshot; }
        private GeneratedAnswer deterministicAnswer() { return deterministicAnswer; }
        private List<DiagnosticEvent> events() { return events; }
    }
}
