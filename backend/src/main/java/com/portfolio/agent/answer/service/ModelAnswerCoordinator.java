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
import org.springframework.stereotype.Component;

@Component
public final class ModelAnswerCoordinator {

    private final AnswerEngine deterministicAnswerEngine;
    private final ModelExpressionPort modelExpressionPort;
    private final AnswerOutputValidator outputValidator;

    public ModelAnswerCoordinator(
            AnswerEngine deterministicAnswerEngine,
            ModelExpressionPort modelExpressionPort,
            AnswerOutputValidator outputValidator
    ) {
        this.deterministicAnswerEngine = deterministicAnswerEngine;
        this.modelExpressionPort = modelExpressionPort;
        this.outputValidator = outputValidator;
    }

    public ModelAnswerOutcome generate(
            AgentExecutionSnapshot execution,
            AnswerPlan plan
    ) {
        if (!execution.isModelExpressionEnabled()) {
            return deterministic(plan, GenerationMode.DETERMINISTIC, null);
        }
        ModelExpressionResult result;
        try {
            result = modelExpressionPort.express(new ModelExpressionRequest(
                    execution.getAnswerSchemaVersion(), plan));
        } catch (RuntimeException exception) {
            return deterministic(
                    plan,
                    GenerationMode.FALLBACK,
                    ModelExpressionFailureCode.PROVIDER_ERROR
            );
        }
        if (result == null || !result.isSuccessful()) {
            ModelExpressionFailureCode failureCode = result == null
                    ? ModelExpressionFailureCode.INVALID_RESPONSE
                    : result.getFailureCode();
            return deterministic(plan, GenerationMode.FALLBACK, failureCode);
        }
        AnswerValidationResult validation;
        try {
            validation = outputValidator.validate(plan, result.getDraft());
        } catch (RuntimeException exception) {
            return deterministic(
                    plan,
                    GenerationMode.FALLBACK,
                    ModelExpressionFailureCode.DRAFT_REJECTED
            );
        }
        if (!validation.isAccepted()) {
            return deterministic(
                    plan,
                    GenerationMode.FALLBACK,
                    ModelExpressionFailureCode.DRAFT_REJECTED
            );
        }
        return new ModelAnswerOutcome(
                validation.getAnswer(), GenerationMode.MODEL, null);
    }

    private ModelAnswerOutcome deterministic(
            AnswerPlan plan,
            GenerationMode generationMode,
            ModelExpressionFailureCode failureCode
    ) {
        GeneratedAnswer answer = deterministicAnswerEngine.answer(plan);
        return new ModelAnswerOutcome(answer, generationMode, failureCode);
    }
}
