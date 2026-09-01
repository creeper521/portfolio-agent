package com.portfolio.agent.turn.continuation;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectDiscussionCoordinatorTest {
    private static final Instant NOW =
            Instant.parse("2026-08-20T08:00:00Z");

    private final ProjectDiscussionCoordinator coordinator =
            new ProjectDiscussionCoordinator(
                    () -> "discussion_handle_123",
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    Duration.ofMinutes(30));

    @Test
    void enterCopiesExactlyTheRecommendationResultsAndBuildsOverviewGoal() {
        ContinuationContext.Recommendation recommendation = recommendation();

        ProjectDiscussionCoordinator.Transition transition =
                coordinator.enter(
                        "conversation-1", "release-1",
                        recommendation, "item-b",
                        Set.of("project-a", "project-b"),
                        NOW.plus(Duration.ofMinutes(20)));

        assertThat(transition.context().getProjectId())
                .isEqualTo("project-b");
        assertThat(transition.context().getSwitchCandidateProjectIds())
                .containsExactlyInAnyOrder("project-a", "project-b");
        assertThat(transition.context().getExpiresAt())
                .isEqualTo(NOW.plus(Duration.ofMinutes(20)));
        assertThat(transition.overviewGoal().getGoals().getFirst()
                .getSubjectCandidates().getFirst().getReference())
                .isEqualTo("project-b");
    }

    @Test
    void rejectsItemsOutsideTheRecommendationAndPublicRelease() {
        assertThatThrownBy(() -> coordinator.enter(
                "conversation-1", "release-1",
                recommendation(), "item-c",
                Set.of("project-a", "project-b"),
                NOW.plus(Duration.ofMinutes(30))))
                .isInstanceOfSatisfying(
                        ProjectDiscussionCoordinator.Rejection.class,
                        rejection -> assertThat(rejection.getReason()).isEqualTo(
                                ProjectDiscussionCoordinator.RejectionReason
                                        .RESULT_ITEM_NOT_IN_CONTEXT));
        assertThatThrownBy(() -> coordinator.enter(
                "conversation-1", "release-1",
                recommendation(), "item-b",
                Set.of("project-a"),
                NOW.plus(Duration.ofMinutes(30))))
                .isInstanceOfSatisfying(
                        ProjectDiscussionCoordinator.Rejection.class,
                        rejection -> assertThat(rejection.getReason()).isEqualTo(
                                ProjectDiscussionCoordinator.RejectionReason
                                        .RECOMMENDATION_CANDIDATE_NOT_CURRENT_PUBLIC_PROJECT));
        assertThatThrownBy(() -> coordinator.enter(
                "conversation-1", "release-2",
                recommendation(), "item-b",
                Set.of("project-a", "project-b"),
                NOW.plus(Duration.ofMinutes(30))))
                .isInstanceOfSatisfying(
                        ProjectDiscussionCoordinator.Rejection.class,
                        rejection -> assertThat(rejection.getReason()).isEqualTo(
                                ProjectDiscussionCoordinator.RejectionReason
                                        .CONTEXT_RELEASE_MISMATCH));
    }

    @Test
    void reenterCreatesANewSingleProjectScope() {
        ProjectDiscussionCoordinator.Transition transition =
                coordinator.reenter(
                        "conversation-1", "release-1", "project-b",
                        Set.of("project-a", "project-b"),
                        NOW.plus(Duration.ofMinutes(30)));

        assertThat(transition.context().getSwitchCandidateProjectIds())
                .containsExactly("project-b");
        assertThat(transition.context().getSourceRecommendationHandle())
                .isNull();
    }

    @Test
    void switchKeepsTheFrozenCandidateSetAndUsesTheSessionBound() {
        ProjectDiscussionContext current = new ProjectDiscussionContext(
                "discussion_current_123", "conversation-1", "release-1",
                NOW.plus(Duration.ofMinutes(5)), "project-a",
                Set.of("project-a", "project-b"), NOW,
                "recommendation_handle_123");

        ProjectDiscussionCoordinator.Transition transition =
                coordinator.switchProject(
                        current, "project-b",
                        Set.of("project-a", "project-b"),
                        NOW.plus(Duration.ofMinutes(25)));

        assertThat(transition.context().getSwitchCandidateProjectIds())
                .containsExactlyInAnyOrder("project-a", "project-b");
        assertThat(transition.context().getExpiresAt())
                .isEqualTo(NOW.plus(Duration.ofMinutes(25)));
    }

    private ContinuationContext.Recommendation recommendation() {
        return new ContinuationContext.Recommendation(
                "recommendation_handle_123",
                "conversation-1",
                "release-1",
                NOW.plus(Duration.ofMinutes(2)),
                true,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                2,
                List.of(
                        new ContinuationContext.ResultItem(
                                "item-a", "project-a"),
                        new ContinuationContext.ResultItem(
                                "item-b", "project-b")));
    }
}
