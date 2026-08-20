package com.portfolio.agent.turn.continuation;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "kind", visible = false)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ContinuationContext.Recommendation.class,
                name = "RECOMMENDATION"),
        @JsonSubTypes.Type(value = ProjectDiscussionContext.class,
                name = "PROJECT_DISCUSSION")
})
public abstract sealed class ContinuationContext permits
        ContinuationContext.Recommendation,
        ProjectDiscussionContext {
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
    public enum Kind {
        RECOMMENDATION,
        PROJECT_DISCUSSION
    }

    public static final class Recommendation extends ContinuationContext {
        private final boolean allPublishedAuthorized;
        private final Set<String> authorizedSubjectIds;
        private final Set<String> constraints;
        private final Set<String> preferences;
        private final Set<String> exclusions;
        private final int resultLimit;
        private final List<ResultItem> selectedResults;

        public Recommendation(
                String contextHandle, String conversationId, String contentReleaseId, Instant expiresAt,
                boolean allPublishedAuthorized, Set<String> authorizedSubjectIds, Set<String> constraints,
                Set<String> preferences, Set<String> exclusions, int resultLimit,
                List<ResultItem> selectedResults) {
            super(contextHandle, conversationId, contentReleaseId, expiresAt);
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
        public List<ResultItem> getSelectedResults() { return selectedResults; }

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
