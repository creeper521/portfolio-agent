package com.portfolio.agent.answer.composition.domain;

import java.util.Objects;

public final class CandidateReference {
    private final String publicLabel;

    public CandidateReference(String publicLabel) {
        this.publicLabel = DomainValues.requireText(publicLabel, "publicLabel");
    }

    public String getPublicLabel() { return publicLabel; }

    @Override public boolean equals(Object other) {
        return this == other || other instanceof CandidateReference that
                && publicLabel.equals(that.publicLabel);
    }
    @Override public int hashCode() { return Objects.hash(publicLabel); }
    @Override public String toString() { return "CandidateReference{published=true}"; }
}
