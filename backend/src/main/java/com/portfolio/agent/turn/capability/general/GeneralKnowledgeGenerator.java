package com.portfolio.agent.turn.capability.general;

import com.portfolio.agent.common.observability.ModelOutputDiagnostics;

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

    public GeneralSemanticResult generate(GeneralKnowledgeRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.getDeadline().isExpired()) {
            throw new GeneralKnowledgeUnavailableException("general capability is unavailable");
        }
        try {
            String output = modelPort.generate(request);
            GeneralDraftCodec.Draft draft;
            try {
                draft = codec.decode(output);
            } catch (RuntimeException exception) {
                outputDiagnostics.rejected(
                        "GENERAL_KNOWLEDGE", ModelOutputDiagnostics.Layer.SCHEMA);
                throw exception;
            }
            try {
                return validator.validate(request, draft);
            } catch (GeneralDraftValidationException exception) {
                outputDiagnostics.rejected(
                        "GENERAL_KNOWLEDGE", ModelOutputDiagnostics.Layer.SEMANTIC,
                        exception.getReason().name());
                throw exception;
            } catch (RuntimeException exception) {
                outputDiagnostics.rejected(
                        "GENERAL_KNOWLEDGE", ModelOutputDiagnostics.Layer.SEMANTIC);
                throw exception;
            }
        } catch (GeneralKnowledgeUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new GeneralKnowledgeUnavailableException("general generation failed", exception);
        }
    }
}
