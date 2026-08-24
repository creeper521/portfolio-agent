package com.portfolio.agent.turn.capability.general;

import com.portfolio.agent.common.observability.ModelOutputDiagnostics;
import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.infrastructure.model.ModelExecutionSnapshot;
import com.portfolio.agent.infrastructure.model.SelectedModelFailureException;

import java.util.Objects;

/** One call, one strict decode and one semantic validation. No retry or fallback. */
public final class GeneralKnowledgeGenerator {
    private final GeneralKnowledgeModelPort modelPort;
    private final GeneralDraftCodec codec;
    private final GeneralDraftValidator validator;
    private final ModelOutputDiagnostics outputDiagnostics;

    public GeneralKnowledgeGenerator(
            GeneralKnowledgeModelPort modelPort,
            GeneralDraftCodec codec,
            GeneralDraftValidator validator) {
        this(modelPort, codec, validator, ModelOutputDiagnostics.none());
    }

    public GeneralKnowledgeGenerator(
            GeneralKnowledgeModelPort modelPort,
            GeneralDraftCodec codec,
            GeneralDraftValidator validator,
            ModelOutputDiagnostics outputDiagnostics) {
        this.modelPort = Objects.requireNonNull(modelPort, "modelPort");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.outputDiagnostics = Objects.requireNonNull(
                outputDiagnostics, "outputDiagnostics");
    }

    public GeneralSemanticResult generate(
            GeneralKnowledgeRequest request,
            ResolvedModelExecution modelExecution) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(modelExecution, "modelExecution");
        if (request.getDeadline().isExpired()) {
            if (modelExecution.getSnapshot().getKind()
                    == ModelExecutionSnapshot.Kind.MODEL) {
                throw SelectedModelFailureException
                        .temporarilyUnavailableBeforeAttempt();
            }
            throw new GeneralKnowledgeUnavailableException("general capability is unavailable");
        }
        try {
            String output = modelPort.generate(request, modelExecution);
            GeneralDraftCodec.Draft draft;
            try {
                draft = codec.decode(output);
            } catch (RuntimeException exception) {
                outputDiagnostics.rejected(
                        "GENERAL_KNOWLEDGE", ModelOutputDiagnostics.Layer.SCHEMA);
                throw invalidResponse(modelExecution, exception);
            }
            try {
                GeneralSemanticResult result = validator.validate(request, draft);
                modelExecution.markAdopted(
                        ResolvedModelExecution.Stage.ANSWER_GENERATION);
                return result;
            } catch (GeneralDraftValidationException exception) {
                outputDiagnostics.rejected(
                        "GENERAL_KNOWLEDGE", ModelOutputDiagnostics.Layer.SEMANTIC,
                        exception.getReason().name());
                throw invalidResponse(modelExecution, exception);
            } catch (RuntimeException exception) {
                outputDiagnostics.rejected(
                        "GENERAL_KNOWLEDGE", ModelOutputDiagnostics.Layer.SEMANTIC);
                throw invalidResponse(modelExecution, exception);
            }
        } catch (SelectedModelFailureException exception) {
            throw exception;
        } catch (GeneralKnowledgeUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new GeneralKnowledgeUnavailableException("general generation failed", exception);
        }
    }

    GeneralSemanticResult generate(GeneralKnowledgeRequest request) {
        return generate(request, ResolvedModelExecution.none());
    }

    private RuntimeException invalidResponse(
            ResolvedModelExecution modelExecution,
            RuntimeException cause) {
        if (modelExecution.getSnapshot().getKind()
                == ModelExecutionSnapshot.Kind.MODEL) {
            return SelectedModelFailureException.invalidResponse(cause);
        }
        return new GeneralKnowledgeUnavailableException(
                "general generation failed", cause);
    }
}
