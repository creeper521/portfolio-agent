package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AgentExecutionSnapshot;
import com.portfolio.agent.answer.domain.AnswerPlan;
import com.portfolio.agent.answer.domain.GeneratedAnswer;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.domain.ModelExpressionFailureCode;
import com.portfolio.agent.answer.domain.ModelExpressionRequest;
import com.portfolio.agent.answer.domain.ModelExpressionResult;
import com.portfolio.agent.answer.engine.AnswerEngine;
import com.portfolio.agent.answer.gateway.ModelExpressionPort;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.DiagnosticLevel;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public final class ModelAnswerCoordinator {

    private final AnswerEngine deterministicAnswerEngine;
    private final ModelExpressionPort modelExpressionPort;
    private final AnswerOutputValidator outputValidator;
    private final DiagnosticEventPublisher diagnosticEventPublisher;

    public ModelAnswerCoordinator(
            AnswerEngine deterministicAnswerEngine,
            ModelExpressionPort modelExpressionPort,
            AnswerOutputValidator outputValidator,
            DiagnosticEventPublisher diagnosticEventPublisher
    ) {
        this.deterministicAnswerEngine = deterministicAnswerEngine;
        this.modelExpressionPort = modelExpressionPort;
        this.outputValidator = outputValidator;
        this.diagnosticEventPublisher = Objects.requireNonNull(
                diagnosticEventPublisher,
                "diagnosticEventPublisher");
    }

    public ModelAnswerOutcome generate(
            AgentExecutionSnapshot execution,
            AnswerPlan plan
    ) {
        if (!execution.isModelExpressionEnabled()) {
            return deterministic(plan, GenerationMode.DETERMINISTIC, null, null);
        }
        ModelExpressionResult result;
        try {
            result = modelExpressionPort.express(new ModelExpressionRequest(
                    execution.getAnswerSchemaVersion(), plan));
        } catch (RuntimeException exception) {
            return deterministic(
                    plan,
                    GenerationMode.FALLBACK,
                    ModelExpressionFailureCode.PROVIDER_ERROR,
                    FallbackTrigger.PROVIDER_FAILURE
            );
        }
        if (result == null || !result.isSuccessful()) {
            ModelExpressionFailureCode failureCode = result == null
                    ? ModelExpressionFailureCode.INVALID_RESPONSE
                    : result.getFailureCode();
            return deterministic(
                    plan,
                    GenerationMode.FALLBACK,
                    failureCode,
                    FallbackTrigger.PROVIDER_FAILURE);
        }
        AnswerValidationResult validation;
        long validationStartedAt = System.nanoTime();
        try {
            validation = outputValidator.validate(plan, result.getDraft());
        } catch (RuntimeException exception) {
            return deterministic(
                    plan,
                    GenerationMode.FALLBACK,
                    ModelExpressionFailureCode.DRAFT_REJECTED,
                    FallbackTrigger.VALIDATION_EXCEPTION
            );
        }
        publishValidation(validation, validationStartedAt);
        if (!validation.isAccepted()) {
            return deterministic(
                    plan,
                    GenerationMode.FALLBACK,
                    ModelExpressionFailureCode.DRAFT_REJECTED,
                    FallbackTrigger.VALIDATION_REJECTED
            );
        }
        return new ModelAnswerOutcome(
                validation.getAnswer(), GenerationMode.MODEL, null);
    }

    private ModelAnswerOutcome deterministic(
            AnswerPlan plan,
            GenerationMode generationMode,
            ModelExpressionFailureCode failureCode,
            FallbackTrigger fallbackTrigger
    ) {
        GeneratedAnswer answer = deterministicAnswerEngine.answer(plan);
        if (generationMode == GenerationMode.FALLBACK) {
            publishFallback(fallbackTrigger, failureCode);
        }
        return new ModelAnswerOutcome(answer, generationMode, failureCode);
    }

    private void publishValidation(
            AnswerValidationResult validation,
            long startedAt
    ) {
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        String failureCode = validation.isAccepted()
                ? "NONE"
                : validation.getFailureCode().name();
        try {
            diagnosticEventPublisher.publish(DiagnosticEvent.builder(
                            "answer.validation.completed",
                            validation.isAccepted()
                                    ? DiagnosticLevel.DEBUG
                                    : DiagnosticLevel.WARN)
                    .field("validation.accepted", validation.isAccepted())
                    .field("failure.code", failureCode)
                    .field(
                            "duration.bucket",
                            DurationBuckets.fromElapsedMillis(elapsedMillis))
                    .build());
        } catch (RuntimeException ignored) {
            // Diagnostics must never change answer selection.
        }
    }

    private void publishFallback(
            FallbackTrigger fallbackTrigger,
            ModelExpressionFailureCode failureCode
    ) {
        try {
            diagnosticEventPublisher.publish(DiagnosticEvent.builder(
                            "answer.fallback.selected",
                            DiagnosticLevel.WARN)
                    .field("fallback.trigger", fallbackTrigger)
                    .field(
                            "failure.code",
                            ProviderFailureCodeMapper.map(failureCode))
                    .build());
        } catch (RuntimeException ignored) {
            // Diagnostics must never change answer selection.
        }
    }

    private enum FallbackTrigger {
        PROVIDER_FAILURE,
        VALIDATION_REJECTED,
        VALIDATION_EXCEPTION
    }
}
