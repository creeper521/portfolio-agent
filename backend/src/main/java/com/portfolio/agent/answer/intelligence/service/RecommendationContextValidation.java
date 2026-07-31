package com.portfolio.agent.answer.intelligence.service;

import java.util.Objects;

public final class RecommendationContextValidation {

    private final RecommendationContextValidationFailureCode failureCode;

    private RecommendationContextValidation(RecommendationContextValidationFailureCode failureCode) {
        this.failureCode = failureCode;
    }

    public static RecommendationContextValidation valid() {
        return new RecommendationContextValidation(null);
    }

    public static RecommendationContextValidation invalid(
            RecommendationContextValidationFailureCode failureCode) {
        return new RecommendationContextValidation(Objects.requireNonNull(failureCode, "failureCode"));
    }

    public boolean isValid() {
        return failureCode == null;
    }

    public RecommendationContextValidationFailureCode getFailureCode() {
        return failureCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RecommendationContextValidation that)) {
            return false;
        }
        return failureCode == that.failureCode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(failureCode);
    }

    @Override
    public String toString() {
        return "RecommendationContextValidation{" + "valid=" + isValid()
                + ", failureCode=" + failureCode + '}';
    }
}
