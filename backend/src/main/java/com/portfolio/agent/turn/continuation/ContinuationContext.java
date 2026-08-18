package com.portfolio.agent.turn.continuation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public abstract sealed class ContinuationContext permits
        ContinuationContext.PortfolioFact,
        ContinuationContext.PortfolioComparison,
        ContinuationContext.Recommendation {
    private final String contextHandle;
    private final String conversationId;
    private final String contentReleaseId;
    private final Instant expiresAt;

    protected ContinuationContext(
            String contextHandle, String conversationId,
            String contentReleaseId, Instant expiresAt) {
        this.contextHandle = text(contextHandle, "contextHandle");
        this.conversationId = text(conversationId, "conversationId");
        this.contentReleaseId = text(contentReleaseId, "contentReleaseId");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }
    public String getContextHandle() { return contextHandle; }
    public String getConversationId() { return conversationId; }
    public String getContentReleaseId() { return contentReleaseId; }
    public Instant getExpiresAt() { return expiresAt; }
    public abstract Kind getKind();
    public enum Kind { PORTFOLIO_FACT, PORTFOLIO_COMPARISON, RECOMMENDATION }

    public static final class PortfolioFact extends ContinuationContext {
        private final Set<String> subjectIds;
        private final Set<String> facets;
        public PortfolioFact(
                String handle, String conversationId, String release, Instant expiresAt,
                Set<String> subjectIds, Set<String> facets) {
            super(handle, conversationId, release, expiresAt);
            this.subjectIds = texts(subjectIds, "subjectIds", false);
            this.facets = texts(facets, "facets", false);
        }
        @Override public Kind getKind() { return Kind.PORTFOLIO_FACT; }
        public Set<String> getSubjectIds() { return subjectIds; }
        public Set<String> getFacets() { return facets; }
    }

    public static final class PortfolioComparison extends ContinuationContext {
        private final Set<String> subjectIds;
        private final Set<String> dimensions;
        public PortfolioComparison(
                String handle, String conversationId, String release, Instant expiresAt,
                Set<String> subjectIds, Set<String> dimensions) {
            super(handle, conversationId, release, expiresAt);
            this.subjectIds = texts(subjectIds, "subjectIds", false);
            if (this.subjectIds.size() < 2) throw new IllegalArgumentException("comparison requires subjects");
            this.dimensions = texts(dimensions, "dimensions", false);
        }
        @Override public Kind getKind() { return Kind.PORTFOLIO_COMPARISON; }
        public Set<String> getSubjectIds() { return subjectIds; }
        public Set<String> getDimensions() { return dimensions; }
    }

    public static final class Recommendation extends ContinuationContext {
        private final boolean allPublishedAuthorized;
        private final Set<String> authorizedSubjectIds;
        private final Set<String> constraints;
        private final Set<String> preferences;
        private final Set<String> exclusions;
        private final int resultLimit;
        private final String parentContextHandle;
        private final List<ResultItem> selectedResults;

        public Recommendation(
                String handle, String conversationId, String release, Instant expiresAt,
                boolean allPublishedAuthorized, Set<String> authorizedSubjectIds, Set<String> constraints,
                Set<String> preferences, Set<String> exclusions, int resultLimit,
                String parentContextHandle, List<ResultItem> selectedResults) {
            super(handle, conversationId, release, expiresAt);
            this.allPublishedAuthorized = allPublishedAuthorized;
            this.authorizedSubjectIds = texts(
                    authorizedSubjectIds, "authorizedSubjectIds", allPublishedAuthorized);
            if (allPublishedAuthorized && !this.authorizedSubjectIds.isEmpty()) {
                throw new IllegalArgumentException("all-published scope cannot carry subjects");
            }
            this.constraints = texts(constraints, "constraints", true);
            this.preferences = texts(preferences, "preferences", true);
            this.exclusions = texts(exclusions, "exclusions", true);
            if (resultLimit < 1 || resultLimit > 5) throw new IllegalArgumentException("resultLimit is invalid");
            this.resultLimit = resultLimit;
            this.parentContextHandle = parentContextHandle == null ? null : text(parentContextHandle, "parent");
            this.selectedResults = List.copyOf(Objects.requireNonNull(selectedResults, "selectedResults"));
            if (this.selectedResults.isEmpty() || this.selectedResults.size() > resultLimit
                    || this.selectedResults.stream().map(ResultItem::resultItemId).distinct().count()
                    != this.selectedResults.size()
                    || !allPublishedAuthorized && this.selectedResults.stream().anyMatch(value ->
                    !this.authorizedSubjectIds.contains(value.subjectId()))) {
                throw new IllegalArgumentException("selectedResults are invalid");
            }
        }
        @Override public Kind getKind() { return Kind.RECOMMENDATION; }
        public boolean isAllPublishedAuthorized() { return allPublishedAuthorized; }
        public Set<String> getAuthorizedSubjectIds() { return authorizedSubjectIds; }
        public Set<String> getConstraints() { return constraints; }
        public Set<String> getPreferences() { return preferences; }
        public Set<String> getExclusions() { return exclusions; }
        public int getResultLimit() { return resultLimit; }
        public String getParentContextHandle() { return parentContextHandle; }
        public List<ResultItem> getSelectedResults() { return selectedResults; }

        public Recommendation child(
                String childHandle, String currentRelease, Instant childExpiresAt,
                Set<String> childAuthorizedSubjects, Set<String> additionalConstraints,
                Set<String> childPreferences, Set<String> childExclusions,
                int childResultLimit, List<ResultItem> childResults) {
            Set<String> scope = texts(childAuthorizedSubjects, "childAuthorizedSubjects", false);
            if (!allPublishedAuthorized && !authorizedSubjectIds.containsAll(scope)) {
                throw new IllegalArgumentException("child context cannot expand authorized scope");
            }
            java.util.LinkedHashSet<String> mergedConstraints = new java.util.LinkedHashSet<>(constraints);
            mergedConstraints.addAll(texts(additionalConstraints, "additionalConstraints", true));
            java.util.LinkedHashSet<String> mergedExclusions = new java.util.LinkedHashSet<>(exclusions);
            mergedExclusions.addAll(texts(childExclusions, "childExclusions", true));
            return new Recommendation(
                    childHandle, getConversationId(), currentRelease, childExpiresAt,
                    false, scope, mergedConstraints, childPreferences, mergedExclusions,
                    childResultLimit, getContextHandle(), childResults);
        }
    }

    public record ResultItem(String resultItemId, String subjectId) {
        public ResultItem {
            resultItemId = text(resultItemId, "resultItemId");
            subjectId = text(subjectId, "subjectId");
        }
    }

    static String text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
    private static Set<String> texts(Set<String> values, String name, boolean allowEmpty) {
        Set<String> copied = Set.copyOf(Objects.requireNonNull(values, name));
        if (!allowEmpty && copied.isEmpty() || copied.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return copied;
    }
}
