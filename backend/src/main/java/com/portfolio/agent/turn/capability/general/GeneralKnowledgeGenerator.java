package com.portfolio.agent.turn.capability.general;

import java.util.Objects;

/** One call, one strict decode and one semantic validation. No retry or fallback. */
public final class GeneralKnowledgeGenerator {
    private final GeneralKnowledgeModelPort modelPort;
    private final GeneralDraftCodec codec;
    private final GeneralDraftValidator validator;

    public GeneralKnowledgeGenerator(
            GeneralKnowledgeModelPort modelPort,
            GeneralDraftCodec codec,
            GeneralDraftValidator validator) {
        this.modelPort = Objects.requireNonNull(modelPort, "modelPort");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public GeneralSemanticResult generate(GeneralKnowledgeRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.getDeadline().isExpired()) {
            throw new GeneralKnowledgeUnavailableException("general capability is unavailable");
        }
        try {
            return validator.validate(request, codec.decode(modelPort.generate(request)));
        } catch (GeneralKnowledgeUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new GeneralKnowledgeUnavailableException("general generation failed", exception);
        }
    }
}
