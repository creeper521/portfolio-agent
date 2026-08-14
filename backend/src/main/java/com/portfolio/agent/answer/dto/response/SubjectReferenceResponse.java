package com.portfolio.agent.answer.dto.response;

import java.util.Objects;

/** Public subject identity used by ordered recommendation continuation. */
public final class SubjectReferenceResponse {
    private final String subjectType;
    private final String subjectId;

    public SubjectReferenceResponse(String subjectType, String subjectId) {
        this.subjectType = requireText(subjectType, "subjectType");
        this.subjectId = requireText(subjectId, "subjectId");
    }

    public String getSubjectType() { return subjectType; }
    public String getSubjectId() { return subjectId; }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
