package com.portfolio.agent.answer.intelligence.execution.domain;

import com.portfolio.agent.answer.routing.domain.SubjectReference;

import java.util.List;
import java.util.Objects;

/** Immutable authorization inherited by a recommendation refinement. */
public final class RecommendationScopeBinding {

    private final AuthorizedSubjectScope scope;
    private final String contentVersion;

    public RecommendationScopeBinding(AuthorizedSubjectScope scope, String contentVersion) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.contentVersion = requireText(contentVersion, "contentVersion");
        if (!this.contentVersion.equals(scope.getContentVersion())) {
            throw new IllegalArgumentException("binding and scope content versions must match");
        }
    }

    public AuthorizedSubjectScope getScope() {
        return scope;
    }

    public AuthorizedSubjectScope.ScopeMode getScopeMode() {
        return scope.getMode();
    }

    public List<SubjectReference> getExactSubjectReferences() {
        return scope.getExactSubjects();
    }

    public String getContentVersion() {
        return contentVersion;
    }

    public boolean permits(SubjectReference subject) {
        return scope.contains(subject);
    }

    public boolean canRefineTo(List<SubjectReference> requestedSubjects) {
        Objects.requireNonNull(requestedSubjects, "requestedSubjects");
        if (scope.getMode() == AuthorizedSubjectScope.ScopeMode.ALL_PUBLISHED_CANDIDATES) {
            return true;
        }
        return requestedSubjects.stream().allMatch(scope::contains);
    }

    @Override
    public String toString() {
        return "RecommendationScopeBinding{scopeMode=" + scope.getMode() + '}';
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
