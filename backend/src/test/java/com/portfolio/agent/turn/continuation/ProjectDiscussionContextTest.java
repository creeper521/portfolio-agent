package com.portfolio.agent.turn.continuation;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectDiscussionContextTest {

    private static final Instant STARTED =
            Instant.parse("2026-08-20T08:00:00Z");
    private static final Instant EXPIRES =
            Instant.parse("2026-08-20T08:30:00Z");

    @Test
    void locksOnePublicProjectAndABoundedSwitchScope() {
        ProjectDiscussionContext context = new ProjectDiscussionContext(
                "discussion_handle_123",
                "conversation-1",
                "release-1",
                EXPIRES,
                "project-b",
                Set.of("project-a", "project-b", "project-c"),
                STARTED,
                "recommendation_handle_123");

        assertThat(context.getKind())
                .isEqualTo(ContinuationContext.Kind.PROJECT_DISCUSSION);
        assertThat(context.getProjectId()).isEqualTo("project-b");
        assertThat(context.getSwitchCandidateProjectIds())
                .containsExactlyInAnyOrder(
                        "project-a", "project-b", "project-c");
        assertThat(context.getSourceRecommendationHandle())
                .isEqualTo("recommendation_handle_123");
    }

    @Test
    void refusesScopeExpansionAndInvalidLifetime() {
        assertThatThrownBy(() -> new ProjectDiscussionContext(
                "discussion_handle_123",
                "conversation-1",
                "release-1",
                EXPIRES,
                "project-b",
                Set.of("project-a"),
                STARTED,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("locked project");
        assertThatThrownBy(() -> new ProjectDiscussionContext(
                "discussion_handle_123",
                "conversation-1",
                "release-1",
                STARTED,
                "project-b",
                Set.of("project-b"),
                STARTED,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lifetime");
    }
}
