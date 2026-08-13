package com.portfolio.agent.answer.context.domain;

import com.portfolio.agent.answer.intelligence.execution.domain.AuthorizedSubjectScope;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Immutable recommendation authorization and rule state; no answer text is retained. */
public final class RecommendationContext {
    private final AuthorizedSubjectScope authorizedScope;
    private final String profileVersion;
    private final Set<String> baselineCriteria;
    private final Set<String> constraints;
    private final Set<String> preferences;
    private final Set<String> exclusions;
    private final int resultLimit;
    private final ContextHandle parentContextHandle;

    public RecommendationContext(
            AuthorizedSubjectScope authorizedScope, String profileVersion, Set<String> baselineCriteria,
            Set<String> constraints, Set<String> preferences, Set<String> exclusions,
            int resultLimit, ContextHandle parentContextHandle) {
        this.authorizedScope = Objects.requireNonNull(authorizedScope, "authorizedScope");
        this.profileVersion = requireText(profileVersion, "profileVersion");
        this.baselineCriteria = normalized(baselineCriteria, "baselineCriteria");
        this.constraints = normalized(constraints, "constraints");
        this.preferences = normalized(preferences, "preferences");
        this.exclusions = normalized(exclusions, "exclusions");
        if (resultLimit < 1 || resultLimit > 5) throw new IllegalArgumentException("resultLimit must be between 1 and 5");
        this.resultLimit = resultLimit;
        this.parentContextHandle = parentContextHandle;
    }
    public AuthorizedSubjectScope getAuthorizedScope() { return authorizedScope; }
    public String getProfileVersion() { return profileVersion; }
    public Set<String> getBaselineCriteria() { return baselineCriteria; }
    public Set<String> getConstraints() { return constraints; }
    public Set<String> getPreferences() { return preferences; }
    public Set<String> getExclusions() { return exclusions; }
    public int getResultLimit() { return resultLimit; }
    public ContextHandle getParentContextHandle() { return parentContextHandle; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecommendationContext that)) return false;
        return resultLimit == that.resultLimit
                && authorizedScope.equals(that.authorizedScope)
                && profileVersion.equals(that.profileVersion)
                && baselineCriteria.equals(that.baselineCriteria)
                && constraints.equals(that.constraints)
                && preferences.equals(that.preferences)
                && exclusions.equals(that.exclusions)
                && Objects.equals(parentContextHandle, that.parentContextHandle);
    }
    @Override public int hashCode() {
        return Objects.hash(authorizedScope, profileVersion, baselineCriteria, constraints,
                preferences, exclusions, resultLimit, parentContextHandle);
    }
    @Override public String toString() { return "RecommendationContext{profileVersion=" + profileVersion + ", resultLimit=" + resultLimit + ", hasParent=" + (parentContextHandle != null) + '}'; }
    private static Set<String> normalized(Set<String> values, String name) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : Objects.requireNonNull(values, name)) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " contains blank");
            result.add(value.trim().toUpperCase(java.util.Locale.ROOT));
        }
        return Set.copyOf(result);
    }
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
