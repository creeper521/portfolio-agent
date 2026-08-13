package com.portfolio.agent.answer.context.domain;

import java.util.Objects;

/** Closed, typed union of the business Context payloads supported by P3. */
public final class ConversationContextValue {
    private final ConversationContextType type;
    private final RecentSemanticTaskContext recentSemanticTaskContext;
    private final RecommendationContext recommendationContext;

    private ConversationContextValue(
            ConversationContextType type,
            RecentSemanticTaskContext recentSemanticTaskContext,
            RecommendationContext recommendationContext) {
        this.type = Objects.requireNonNull(type, "type");
        this.recentSemanticTaskContext = recentSemanticTaskContext;
        this.recommendationContext = recommendationContext;
    }

    public static ConversationContextValue recentSemanticTask(RecentSemanticTaskContext context) {
        return new ConversationContextValue(
                ConversationContextType.RECENT_SEMANTIC_TASK,
                Objects.requireNonNull(context, "context"), null);
    }

    public static ConversationContextValue recommendation(RecommendationContext context) {
        return new ConversationContextValue(
                ConversationContextType.RECOMMENDATION,
                null, Objects.requireNonNull(context, "context"));
    }

    public ConversationContextType getType() {
        return type;
    }

    public RecentSemanticTaskContext getRecentSemanticTaskContext() {
        if (recentSemanticTaskContext == null) {
            throw new IllegalStateException("Context value is not recent semantic task");
        }
        return recentSemanticTaskContext;
    }

    public RecommendationContext getRecommendationContext() {
        if (recommendationContext == null) {
            throw new IllegalStateException("Context value is not recommendation");
        }
        return recommendationContext;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConversationContextValue that)) {
            return false;
        }
        return type == that.type
                && Objects.equals(recentSemanticTaskContext, that.recentSemanticTaskContext)
                && Objects.equals(recommendationContext, that.recommendationContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, recentSemanticTaskContext, recommendationContext);
    }

    @Override
    public String toString() {
        return "ConversationContextValue{type=" + type + '}';
    }
}
