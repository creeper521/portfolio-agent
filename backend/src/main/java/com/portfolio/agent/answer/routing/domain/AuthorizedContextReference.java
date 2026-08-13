package com.portfolio.agent.answer.routing.domain;

import com.portfolio.agent.answer.intelligence.execution.domain.RecommendationScopeBinding;

import java.util.Objects;

/** Opaque, already-authorized reference to a server-side business Context. */
public final class AuthorizedContextReference {

    private final String contextHandle;
    private final String expectedContextType;
    private final RecommendationScopeBinding recommendationScopeBinding;

    public AuthorizedContextReference(String contextHandle, String expectedContextType) {
        this(contextHandle, expectedContextType, null);
    }

    public AuthorizedContextReference(
            String contextHandle,
            String expectedContextType,
            RecommendationScopeBinding recommendationScopeBinding) {
        this.contextHandle = requireOpaqueText(contextHandle, "contextHandle");
        this.expectedContextType = requireOpaqueText(expectedContextType, "expectedContextType");
        if (!expectedContextType.equals("RECENT_SEMANTIC_TASK")
                && !expectedContextType.equals("RECOMMENDATION")) {
            throw new IllegalArgumentException("expectedContextType must be supported");
        }
        this.recommendationScopeBinding = recommendationScopeBinding;
    }

    public String getContextHandle() {
        return contextHandle;
    }

    public String getExpectedContextType() {
        return expectedContextType;
    }

    public java.util.Optional<RecommendationScopeBinding> getRecommendationScopeBinding() {
        return java.util.Optional.ofNullable(recommendationScopeBinding);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AuthorizedContextReference that)) {
            return false;
        }
        return contextHandle.equals(that.contextHandle)
                && expectedContextType.equals(that.expectedContextType)
                && Objects.equals(recommendationScopeBinding, that.recommendationScopeBinding);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contextHandle, expectedContextType, recommendationScopeBinding);
    }

    @Override
    public String toString() {
        return "AuthorizedContextReference{expectedContextType=" + expectedContextType + '}';
    }

    private static String requireOpaqueText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > 192 || normalized.contains("\n") || normalized.contains("\r")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return normalized;
    }
}
