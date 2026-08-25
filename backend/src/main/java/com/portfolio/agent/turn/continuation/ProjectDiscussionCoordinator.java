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

/**
 * Deterministic enter/switch/reenter authority; no language interpretation.
 *
 * <p>项目讨论协调器：确定性地执行进入/切换/重入项目讨论。不做任何语言
 * 解释，只消费已解析的句柄、结果项与公开项目集合；每次迁移签发新的
 * ProjectDiscussionContext 与 ActiveDiscussionPointer，并附带固定形状的
 * 概览目标提案。</p>
 */
public final class ProjectDiscussionCoordinator {
    private final Supplier<String> handleIssuer;
    private final Clock clock;
    private final Duration ttl;

    /** 构造协调器；讨论 TTL 必须为正且不超过 30 分钟。 */
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

    /**
     * 从推荐结果项进入项目讨论。
     *
     * <p>以推荐的全部结果项作为讨论内可切换候选，校验候选仍在当前公开
     * 发布中，并记录来源推荐句柄。</p>
     *
     * @throws IllegalArgumentException 推荐范围与会话不符、结果项不在推荐内，
     *         或候选项目不在当前公开发布中
     */
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
                sessionExpiresAt);
    }

    /**
     * 重入某个公开项目的讨论（无来源推荐，切换候选仅自身）。
     *
     * @throws IllegalArgumentException 项目不在当前公开发布中
     */
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

    /**
     * 在当前讨论范围内切换到另一候选项目。
     *
     * <p>目标项目必须属于原讨论签发的切换候选集合，且全部候选仍须在当前
     * 公开发布中可用；来源推荐句柄与候选集合沿用原讨论。</p>
     *
     * @throws IllegalArgumentException 目标不在讨论候选范围内，
     *         或候选项目已不在当前公开发布中
     */
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

    /**
     * 执行一次讨论迁移：签发新 ContextHandle，过期时间取讨论 TTL 与会话
     * 上界的较早者，产出配套的 {@link ProjectDiscussionContext}、
     * {@link ActiveDiscussionPointer} 与固定形状的概览目标提案。
     */
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

    /** 构造进入讨论时的固定概览提案：单一 PORTFOLIO_FACT 目标覆盖五个公开侧面。 */
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
                                        UserGoalProposal.Facet.STATUS),
                                UserGoalProposal.Depth.STANDARD))));
    }

    /**
     * 构造澄清后的单侧面事实提案：以 CONTINUATION 依据锁定该项目，
     * 请求输出由给定 facet 映射到同名的 {@link GoalRequestedOutput}。
     *
     * @throws IllegalArgumentException facet 为 null
     */
    public UserGoalProposal fact(
            String projectId, UserGoalProposal.Facet facet) {
        Objects.requireNonNull(facet, "facet");
        UserGoalProposal.InputAnchor anchor =
                new UserGoalProposal.InputAnchor("项目讨论澄清", 0);
        return new UserGoalProposal(List.of(
                new UserGoalProposal.ProposedGoal(
                        "project-discussion-clarified-fact",
                        GoalKind.PORTFOLIO_FACT,
                        anchor,
                        List.of(new GoalSubjectReference(
                                GoalSubjectReference.Kind.PROJECT,
                                projectId,
                                GoalSubjectReference.Basis.CONTINUATION,
                                null)),
                        Set.of(GoalRequestedOutput.valueOf(facet.name())),
                        GoalKnowledgeRequirement.PUBLIC_PORTFOLIO_EVIDENCE,
                        new UserGoalProposal.PortfolioFactParameters(
                                Set.of(facet), UserGoalProposal.Depth.STANDARD))));
    }

    /** fail-closed 校验：所有必需项目必须仍在当前公开发布中，否则拒绝迁移。 */
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

    /** 讨论迁移结果：新讨论上下文、活跃讨论指针与进入时的概览目标提案。 */
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
