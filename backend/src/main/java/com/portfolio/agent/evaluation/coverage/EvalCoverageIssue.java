package com.portfolio.agent.evaluation.coverage;

import java.util.Objects;

public final class EvalCoverageIssue {

    private final String code;
    private final String subjectRef;
    private final String message;

    public EvalCoverageIssue(String code, String subjectRef, String message) {
        this.code = code;
        this.subjectRef = subjectRef;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getSubjectRef() {
        return subjectRef;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EvalCoverageIssue that)) {
            return false;
        }
        return Objects.equals(code, that.code)
                && Objects.equals(subjectRef, that.subjectRef)
                && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, subjectRef, message);
    }
}
