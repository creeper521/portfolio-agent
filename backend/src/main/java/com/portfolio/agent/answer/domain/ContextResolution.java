package com.portfolio.agent.answer.domain;

import java.util.Optional;

public final class ContextResolution {

    private final ContextResolutionType type;
    private final ValidatedContextEnvelope envelope;

    private ContextResolution(
            ContextResolutionType type,
            ValidatedContextEnvelope envelope
    ) {
        this.type = type;
        this.envelope = envelope;
    }

    public static ContextResolution valid(
            ContextResolutionType type,
            ValidatedContextEnvelope envelope
    ) {
        if (type == ContextResolutionType.INVALID) {
            throw new IllegalArgumentException("valid context cannot use INVALID type");
        }
        return new ContextResolution(type, envelope);
    }

    public static ContextResolution invalid() {
        return new ContextResolution(ContextResolutionType.INVALID, null);
    }

    public ContextResolutionType getType() { return type; }
    public Optional<ValidatedContextEnvelope> getEnvelope() {
        return Optional.ofNullable(envelope);
    }
}
