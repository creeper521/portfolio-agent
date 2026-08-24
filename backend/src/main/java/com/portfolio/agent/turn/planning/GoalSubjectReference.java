package com.portfolio.agent.turn.planning;

import java.util.Objects;
import java.util.Optional;

public final class GoalSubjectReference {

    private final Kind kind;
    private final String reference;
    private final Basis basis;
    private final UserGoalProposal.InputAnchor anchor;

    public GoalSubjectReference(
            Kind kind,
            String reference,
            Basis basis,
            UserGoalProposal.InputAnchor anchor) {
        this.kind = Objects.requireNonNull(kind, "kind");
        if (reference == null || reference.isBlank() || reference.length() > 128) {
            throw new IllegalArgumentException("subject reference is required and bounded");
        }
        this.reference = reference;
        this.basis = Objects.requireNonNull(basis, "basis");
        if (basis == Basis.EXPLICIT_INPUT && anchor == null) {
            throw new IllegalArgumentException("explicit subject requires an input anchor");
        }
        this.anchor = anchor;
    }

    public Kind getKind() {
        return kind;
    }

    public String getReference() {
        return reference;
    }

    public Basis getBasis() {
        return basis;
    }

    public Optional<UserGoalProposal.InputAnchor> getAnchor() {
        return Optional.ofNullable(anchor);
    }

    public enum Kind { PROJECT, CASE, RESULT }
    public enum Basis { EXPLICIT_INPUT, SURFACE_HINT, CONTINUATION, RECENT_TURN }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GoalSubjectReference that)) return false;
        return kind == that.kind && reference.equals(that.reference)
                && basis == that.basis && Objects.equals(anchor, that.anchor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, reference, basis, anchor);
    }
}
