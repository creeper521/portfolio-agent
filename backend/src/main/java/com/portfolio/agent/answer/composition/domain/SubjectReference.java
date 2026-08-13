package com.portfolio.agent.answer.composition.domain;

import java.util.Objects;

/** A published label, deliberately without an internal id or route. */
public final class SubjectReference {
    private final String publicLabel;

    public SubjectReference(String publicLabel) {
        this.publicLabel = DomainValues.requireText(publicLabel, "publicLabel");
    }

    public String getPublicLabel() {
        return publicLabel;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof SubjectReference that
                && publicLabel.equals(that.publicLabel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(publicLabel);
    }

    @Override
    public String toString() {
        return "SubjectReference{published=true}";
    }
}
