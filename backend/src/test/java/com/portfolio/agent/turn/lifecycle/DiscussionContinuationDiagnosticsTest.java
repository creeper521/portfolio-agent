package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.turn.continuation.ContinuationContext;
import com.portfolio.agent.turn.continuation.ProjectDiscussionContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DiscussionContinuationDiagnosticsTest {
    private static final Instant EXPIRY =
            Instant.parse("2026-08-28T09:00:00Z");

    @Test
    void distinguishesMissingWrongTypeAndRecommendationContexts() {
        assertThat(AgentTurnLifecycleService.classifyDiscussionContext(null))
                .isEqualTo(AgentTurnLifecycleService
                        .DiscussionContinuationFailure.CONTEXT_NOT_FOUND);
        assertThat(AgentTurnLifecycleService.classifyDiscussionContext(
                new ProjectDiscussionContext(
                        "discussion_handle_123",
                        "conversation-1",
                        "public-1",
                        EXPIRY,
                        "project-a",
                        Set.of("project-a"),
                        EXPIRY.minusSeconds(60),
                        null)))
                .isEqualTo(AgentTurnLifecycleService
                        .DiscussionContinuationFailure.CONTEXT_TYPE_MISMATCH);
        assertThat(AgentTurnLifecycleService.classifyDiscussionContext(
                new ContinuationContext.Recommendation(
                        "recommendation_handle_123",
                        "conversation-1",
                        "public-1",
                        EXPIRY,
                        true,
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        1,
                        List.of(new ContinuationContext.ResultItem(
                                "item-a", "project-a")))))
                .isEqualTo(AgentTurnLifecycleService
                        .DiscussionContinuationFailure.NONE);
    }
}
