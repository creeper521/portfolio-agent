package com.portfolio.agent.answer.intelligence.execution.domain;

import java.util.Objects;

/** Fixed evidence selection limits; callers may only tighten them. */
public final class EvidenceSelectionPolicy {

    public static final int MAX_SUBJECTS = 64;
    public static final int MAX_EVIDENCE_UNITS = 128;
    public static final int MAX_UNITS_PER_SUBJECT = 16;
    public static final int MAX_PUBLIC_REFERENCES = 96;

    private final int subjectLimit;
    private final int evidenceUnitLimit;
    private final int unitsPerSubjectLimit;
    private final int publicReferenceLimit;

    public EvidenceSelectionPolicy(
            int subjectLimit, int evidenceUnitLimit, int unitsPerSubjectLimit,
            int publicReferenceLimit) {
        this.subjectLimit = requireBound(subjectLimit, MAX_SUBJECTS, "subjectLimit");
        this.evidenceUnitLimit = requireBound(evidenceUnitLimit, MAX_EVIDENCE_UNITS, "evidenceUnitLimit");
        this.unitsPerSubjectLimit = requireBound(
                unitsPerSubjectLimit, MAX_UNITS_PER_SUBJECT, "unitsPerSubjectLimit");
        this.publicReferenceLimit = requireBound(
                publicReferenceLimit, MAX_PUBLIC_REFERENCES, "publicReferenceLimit");
    }

    public static EvidenceSelectionPolicy defaults() {
        return new EvidenceSelectionPolicy(
                MAX_SUBJECTS, MAX_EVIDENCE_UNITS, MAX_UNITS_PER_SUBJECT, MAX_PUBLIC_REFERENCES);
    }

    public int getSubjectLimit() {
        return subjectLimit;
    }

    public int getEvidenceUnitLimit() {
        return evidenceUnitLimit;
    }

    public int getUnitsPerSubjectLimit() {
        return unitsPerSubjectLimit;
    }

    public int getPublicReferenceLimit() {
        return publicReferenceLimit;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EvidenceSelectionPolicy that)) {
            return false;
        }
        return subjectLimit == that.subjectLimit && evidenceUnitLimit == that.evidenceUnitLimit
                && unitsPerSubjectLimit == that.unitsPerSubjectLimit
                && publicReferenceLimit == that.publicReferenceLimit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectLimit, evidenceUnitLimit, unitsPerSubjectLimit, publicReferenceLimit);
    }

    @Override
    public String toString() {
        return "EvidenceSelectionPolicy{subjectLimit=" + subjectLimit
                + ", evidenceUnitLimit=" + evidenceUnitLimit + '}';
    }

    private static int requireBound(int value, int maximum, String name) {
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException(name + " must be between 0 and " + maximum);
        }
        return value;
    }
}
