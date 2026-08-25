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
/**
 * 续接上下文：State 中短生命周期的加密 typed 会话上下文基类。
 *
 * <p>由上下文句柄、会话 ID、内容发布 ID 与过期时间构成；两个子类为
 * 推荐上下文与项目讨论上下文。Jackson 以 kind 判别子类型。</p>
 */
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
    /** 上下文类别：推荐结果上下文或项目讨论上下文。 */
    public enum Kind {
        RECOMMENDATION,
        PROJECT_DISCUSSION
    }

    /**
     * 推荐上下文：一次推荐结果的续接凭据。
     *
     * <p>allPublishedAuthorized 为 true 表示授权范围为全部公开主体（不再
     * 枚举主体 ID）；selectedResults 为推荐产生的结果项集合，非全量授权时
     * 每个结果项的主体必须落在授权主体集合内。</p>
     */
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

    /** 推荐结果项：结果项 ID 与其指向的公开主体 ID。 */
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
