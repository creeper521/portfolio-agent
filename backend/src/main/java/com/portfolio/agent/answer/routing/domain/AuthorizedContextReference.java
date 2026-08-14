package com.portfolio.agent.answer.routing.domain;

import com.portfolio.agent.answer.intelligence.execution.domain.RecommendationScopeBinding;

import java.util.Objects;

/** Opaque, already-authorized reference to a server-side business Context. */
public final class AuthorizedContextReference {

    private final String contextHandle;
    private final String expectedContextType;
    private final RecommendationScopeBinding recommendationScopeBinding;
    private final String resultItemId;
    private final SubjectReference selectedSubject;

    public AuthorizedContextReference(String contextHandle, String expectedContextType) {
        this(contextHandle, expectedContextType, null, null);
    }

    public AuthorizedContextReference(
            String contextHandle,
            String expectedContextType,
            RecommendationScopeBinding recommendationScopeBinding) {
        this(contextHandle, expectedContextType, recommendationScopeBinding, null, null);
    }

    public AuthorizedContextReference(
            String contextHandle,
            String expectedContextType,
            RecommendationScopeBinding recommendationScopeBinding,
            String resultItemId) {
        this(contextHandle, expectedContextType, recommendationScopeBinding, resultItemId, null);
    }

    public AuthorizedContextReference(
            String contextHandle,
            String expectedContextType,
            RecommendationScopeBinding recommendationScopeBinding,
            String resultItemId,
            SubjectReference selectedSubject) {
        this.contextHandle = requireOpaqueText(contextHandle, "contextHandle");
        this.expectedContextType = requireOpaqueText(expectedContextType, "expectedContextType");
        if (!expectedContextType.equals("RECENT_SEMANTIC_TASK")
                && !expectedContextType.equals("RECOMMENDATION")) {
            throw new IllegalArgumentException("expectedContextType must be supported");
        }
        this.recommendationScopeBinding = recommendationScopeBinding;
        this.resultItemId = normalizeResultItemId(resultItemId);
        this.selectedSubject = selectedSubject;
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

    public java.util.Optional<String> getResultItemId() {
        return java.util.Optional.ofNullable(resultItemId);
    }
    public java.util.Optional<SubjectReference> getSelectedSubject() {
        return java.util.Optional.ofNullable(selectedSubject);
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
                && Objects.equals(recommendationScopeBinding, that.recommendationScopeBinding)
                && Objects.equals(resultItemId, that.resultItemId)
                && Objects.equals(selectedSubject, that.selectedSubject);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contextHandle, expectedContextType, recommendationScopeBinding,
                resultItemId, selectedSubject);
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

    private static String normalizeResultItemId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireOpaqueText(value, "resultItemId");
    }
}
