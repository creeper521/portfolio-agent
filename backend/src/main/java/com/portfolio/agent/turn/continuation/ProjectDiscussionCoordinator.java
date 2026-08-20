package com.portfolio.agent.turn.continuation;

import com.portfolio.agent.turn.planning.GoalKnowledgeRequirement;
import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.GoalRequestedOutput;
import com.portfolio.agent.turn.planning.GoalSubjectReference;
import com.portfolio.agent.turn.planning.UserGoalProposal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Deterministic enter/switch/reenter authority; no language interpretation. */
public final class ProjectDiscussionCoordinator {
    private final Supplier<String> handleIssuer;
    private final Clock clock;
    private final Duration ttl;

    public ProjectDiscussionCoordinator(
            Supplier<String> handleIssuer, Clock clock, Duration ttl) {
        this.handleIssuer = Objects.requireNonNull(
                handleIssuer, "handleIssuer");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (ttl == null || ttl.isZero() || ttl.isNegative()
                || ttl.compareTo(Duration.ofMinutes(30)) > 0) {
            throw new IllegalArgumentException(
                    "discussion ttl is invalid");
        }
        this.ttl = ttl;
    }

    public Transition enter(
            String conversationId,
            String contentReleaseId,
            ContinuationContext.Recommendation recommendation,
            String resultItemId,
            Set<String> currentPublicProjectIds,
            Instant sessionExpiresAt) {
        Objects.requireNonNull(recommendation, "recommendation");
        if (!recommendation.getConversationId().equals(conversationId)
                || !recommendation.getContentReleaseId()
                .equals(contentReleaseId)) {
            throw new IllegalArgumentException(
                    "recommendation scope does not match conversation");
        }
        ContinuationContext.ResultItem selected =
                recommendation.getSelectedResults().stream()
                .filter(item -> item.resultItemId().equals(resultItemId))
                .findFirst().orElseThrow(() ->
                        new IllegalArgumentException(
                                "result item is outside recommendation"));
        Set<String> candidates = recommendation.getSelectedResults().stream()
                .map(ContinuationContext.ResultItem::subjectId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        requireCurrentPublicProjects(
                candidates, currentPublicProjectIds);
        return transition(
                conversationId, contentReleaseId,
                selected.subjectId(), candidates,
                recommendation.getContextHandle(),
                earlier(recommendation.getExpiresAt(), sessionExpiresAt));
    }

    public Transition reenter(
            String conversationId,
            String contentReleaseId,
            String projectId,
            Set<String> currentPublicProjectIds,
            Instant sessionExpiresAt) {
        requireCurrentPublicProjects(
                Set.of(projectId), currentPublicProjectIds);
        return transition(
                conversationId, contentReleaseId,
                projectId, Set.of(projectId), null, sessionExpiresAt);
    }

    public Transition switchProject(
            ProjectDiscussionContext current,
            String projectId,
            Set<String> currentPublicProjectIds,
            Instant sessionExpiresAt) {
        if (!current.getSwitchCandidateProjectIds().contains(projectId)) {
            throw new IllegalArgumentException(
                    "switch project is outside discussion scope");
        }
        requireCurrentPublicProjects(
                current.getSwitchCandidateProjectIds(),
                currentPublicProjectIds);
        return transition(
                current.getConversationId(),
                current.getContentReleaseId(),
                projectId,
                current.getSwitchCandidateProjectIds(),
                current.getSourceRecommendationHandle(),
                sessionExpiresAt);
    }

    private Transition transition(
            String conversationId,
            String contentReleaseId,
            String projectId,
            Set<String> candidates,
            String sourceRecommendationHandle,
            Instant upperExpiry) {
        Instant startedAt = clock.instant();
        Instant expiresAt = earlier(
                startedAt.plus(ttl), upperExpiry);
        ProjectDiscussionContext context =
                new ProjectDiscussionContext(
                        handleIssuer.get(),
                        conversationId,
                        contentReleaseId,
                        expiresAt,
                        projectId,
                        candidates,
                        startedAt,
                        sourceRecommendationHandle);
        ActiveDiscussionPointer pointer =
                new ActiveDiscussionPointer(
                        context.getContextHandle(),
                        projectId,
                        expiresAt);
        return new Transition(context, pointer, overview(projectId));
    }

    private UserGoalProposal overview(String projectId) {
        UserGoalProposal.InputAnchor anchor =
                new UserGoalProposal.InputAnchor("项目概览", 0);
        return new UserGoalProposal(List.of(
                new UserGoalProposal.ProposedGoal(
                        "project-discussion-overview",
                        GoalKind.PORTFOLIO_FACT,
                        anchor,
                        List.of(new GoalSubjectReference(
                                GoalSubjectReference.Kind.PROJECT,
                                projectId,
                                GoalSubjectReference.Basis.CONTINUATION,
                                null)),
                        Set.of(
                                GoalRequestedOutput.OVERVIEW,
                                GoalRequestedOutput.RESPONSIBILITY,
                                GoalRequestedOutput.SOLUTION,
                                GoalRequestedOutput.VERIFICATION,
                                GoalRequestedOutput.STATUS),
                        GoalKnowledgeRequirement.PUBLIC_PORTFOLIO_EVIDENCE,
                        new UserGoalProposal.PortfolioFactParameters(
                                Set.of(
                                        UserGoalProposal.Facet.OVERVIEW,
                                        UserGoalProposal.Facet.RESPONSIBILITY,
                                        UserGoalProposal.Facet.SOLUTION,
                                        UserGoalProposal.Facet.VERIFICATION,
                                        UserGoalProposal.Facet.STATUS)))));
    }

    private void requireCurrentPublicProjects(
            Set<String> required, Set<String> currentPublicProjectIds) {
        if (!currentPublicProjectIds.containsAll(required)) {
            throw new IllegalArgumentException(
                    "project is unavailable in current public release");
        }
    }

    private Instant earlier(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    public record Transition(
            ProjectDiscussionContext context,
            ActiveDiscussionPointer pointer,
            UserGoalProposal overviewGoal) {
        public Transition {
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(pointer, "pointer");
            Objects.requireNonNull(overviewGoal, "overviewGoal");
        }
    }
}
