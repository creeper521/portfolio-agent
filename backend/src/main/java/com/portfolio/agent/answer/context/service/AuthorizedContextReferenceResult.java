package com.portfolio.agent.answer.context.service;

import com.portfolio.agent.answer.context.domain.ConversationContextResolution;
import com.portfolio.agent.answer.routing.domain.AuthorizedContextReference;

import java.util.Objects;
import java.util.Optional;

/** Authorization result that preserves the fail-closed reason for public projection. */
public final class AuthorizedContextReferenceResult {
    private final ConversationContextResolution resolution;
    private final AuthorizedContextReference reference;
    private final ContextVersionDecision versionDecision;

    public AuthorizedContextReferenceResult(
            ConversationContextResolution resolution, AuthorizedContextReference reference) {
        this(resolution, reference, null);
    }

    public AuthorizedContextReferenceResult(
            ConversationContextResolution resolution, AuthorizedContextReference reference,
            ContextVersionDecision versionDecision) {
        this.resolution = Objects.requireNonNull(resolution, "resolution");
        this.reference = reference;
        this.versionDecision = versionDecision;
    }

    public ConversationContextResolution getResolution() { return resolution; }
    public Optional<AuthorizedContextReference> getReference() { return Optional.ofNullable(reference); }
    public Optional<ContextVersionDecision> getVersionDecision() {
        return Optional.ofNullable(versionDecision);
    }
}
