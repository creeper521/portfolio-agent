package com.portfolio.agent.answer.intelligence.domain;

import java.util.Objects;

public final class StructuredSubjectResolution {

    private final StructuredSubjectResolutionType type;
    private final String subjectId;

    private StructuredSubjectResolution(
            StructuredSubjectResolutionType type,
            String subjectId) {
        this.type = Objects.requireNonNull(type, "type");
        this.subjectId = subjectId == null || subjectId.isBlank()
                ? null : subjectId.trim();
    }

    public static StructuredSubjectResolution none() {
        return new StructuredSubjectResolution(
                StructuredSubjectResolutionType.NONE, null);
    }

    public static StructuredSubjectResolution matched(String subjectId) {
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("subjectId is required");
        }
        return new StructuredSubjectResolution(
                StructuredSubjectResolutionType.MATCHED, subjectId);
    }

    public static StructuredSubjectResolution invalid() {
        return new StructuredSubjectResolution(
                StructuredSubjectResolutionType.INVALID, null);
    }

    public StructuredSubjectResolutionType getType() {
        return type;
    }

    public String getSubjectId() {
        return subjectId;
    }
}
