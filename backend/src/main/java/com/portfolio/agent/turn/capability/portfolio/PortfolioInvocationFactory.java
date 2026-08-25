package com.portfolio.agent.turn.capability.portfolio;

import com.portfolio.agent.turn.capability.portfolio.retrieval.CorpusBackend;
import com.portfolio.agent.turn.capability.portfolio.retrieval.SearchStrategy;
import com.portfolio.agent.turn.execution.TaskExecutionContext;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.UserGoalProposal;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 SemanticTask 装配 {@link PortfolioEvidenceInvocation} 的工厂。
 *
 * <p>只接受 PORTFOLIO 域且未超过截止时间的任务；按 Goal 参数类型（事实/对比/推荐）
 * 推导主体范围与检索切面或维度，再按受众画像重排切面优先级，并固定
 * “PostgreSQL 主检索失败时降级到 BUNDLE 快照”的 fallback 组合。
 */
public final class PortfolioInvocationFactory {
    private final CorpusBackend primaryBackend;

    public PortfolioInvocationFactory(CorpusBackend primaryBackend) {
        this.primaryBackend = java.util.Objects.requireNonNull(primaryBackend, "primaryBackend");
    }

    /**
     * 将 PORTFOLIO 域的 SemanticTask 转换为一次 Evidence 检索调用。
     *
     * <p>事实参数按深度把用户切面展开为检索切面并限定到显式主体；对比参数转为
     * 排序后的维度列表；推荐参数在无显式主体时放宽为全部已发布主体并带上推荐
     * 切面。检索策略随范围推导：EXACT 用精确策略，ALL_PUBLISHED 用混合策略。
     *
     * @param context 当前任务的执行上下文（含任务、截止时间与内容发布 ID）
     * @return 通过全部不变量校验的调用对象
     * @throws IllegalArgumentException 任务不属于 PORTFOLIO 域、截止时间已过期
     *         或 Goal 参数类型不受支持时抛出
     */
    public PortfolioEvidenceInvocation create(TaskExecutionContext context) {
        SemanticTask task = context.getTask();
        if (task.getSourceDomain() != SemanticTask.SourceDomain.PORTFOLIO) {
            throw new IllegalArgumentException("only portfolio tasks are accepted");
        }
        if (context.getDeadline().isExpired()) {
            throw new IllegalArgumentException("deadline is expired");
        }
        List<PortfolioEvidenceInvocation.FacetProfile> facets = new ArrayList<>();
        List<String> dimensions = new ArrayList<>();
        UserGoalProposal.Depth depth = UserGoalProposal.Depth.STANDARD;
        int requestedSize = 0;
        java.util.Set<String> recommendationConstraints = java.util.Set.of();
        AuthorizedSubjectScope scope;
        UserGoalProposal.GoalParameters parameters = task.getParameters().getParameters();
        if (parameters instanceof UserGoalProposal.PortfolioFactParameters fact) {
            scope = AuthorizedSubjectScope.exact(task.getSubjectReferences(), context.getContentReleaseId());
            depth = fact.getDepth();
            UserGoalProposal.Depth requestedDepth = depth;
            fact.getFacets().stream().sorted()
                    .forEach(value -> facets.addAll(facets(value, requestedDepth)));
        } else if (parameters instanceof UserGoalProposal.PortfolioCompareParameters comparison) {
            scope = AuthorizedSubjectScope.exact(task.getSubjectReferences(), context.getContentReleaseId());
            dimensions.addAll(comparison.getDimensions().stream()
                    .sorted().map(Enum::name).toList());
        } else if (parameters instanceof UserGoalProposal.PortfolioRecommendationParameters recommendation) {
            scope = task.getSubjectReferences().isEmpty()
                    ? AuthorizedSubjectScope.allPublished(context.getContentReleaseId())
                    : AuthorizedSubjectScope.exact(task.getSubjectReferences(), context.getContentReleaseId());
            facets.add(PortfolioEvidenceInvocation.FacetProfile.RECOMMENDATION);
            requestedSize = recommendation.getRequestedSize();
            recommendationConstraints = recommendation.getConstraints();
        } else {
            throw new IllegalArgumentException("unsupported portfolio parameters");
        }
        SearchStrategy strategy = scope.getMode() == AuthorizedSubjectScope.Mode.EXACT
                ? SearchStrategy.EXACT : SearchStrategy.HYBRID;
        // PostgreSQL 为主时固定降级到 BUNDLE 快照；混合策略降级时退化为纯关键词检索
        CorpusBackend fallbackBackend = primaryBackend == CorpusBackend.POSTGRESQL
                ? CorpusBackend.BUNDLE : null;
        SearchStrategy fallbackStrategy = primaryBackend == CorpusBackend.POSTGRESQL
                ? (strategy == SearchStrategy.HYBRID ? SearchStrategy.KEYWORD : strategy) : null;
        List<PortfolioEvidenceInvocation.FacetProfile> orderedFacets = prioritize(
                facets.stream().distinct().toList(),
                task.getParameters().getAudienceProfile());
        return new PortfolioEvidenceInvocation(
                task.getType(), scope, orderedFacets, dimensions,
                depth, requestedSize, recommendationConstraints,
                context.getContentReleaseId(), primaryBackend, strategy,
                fallbackBackend, fallbackStrategy);
    }

    /**
     * 按受众画像重排切面顺序，让最符合该受众关注点的切面排在前面。
     *
     * <p>面试官优先看实现与技术决策，导师优先看技术决策与局限，HR 优先看职责与结果；
     * 访客不重排，切面少于 2 个时重排无意义，直接原样返回。
     */
    private List<PortfolioEvidenceInvocation.FacetProfile> prioritize(
            List<PortfolioEvidenceInvocation.FacetProfile> facets,
            com.portfolio.agent.turn.planning.SemanticTaskParameters.AudienceProfile audience) {
        if (audience == com.portfolio.agent.turn.planning.SemanticTaskParameters
                .AudienceProfile.GUEST || facets.size() < 2) {
            return facets;
        }
        List<PortfolioEvidenceInvocation.FacetProfile> priority = switch (audience) {
            case INTERVIEWER -> List.of(
                    PortfolioEvidenceInvocation.FacetProfile.IMPLEMENTATION,
                    PortfolioEvidenceInvocation.FacetProfile.TECHNICAL_DECISION,
                    PortfolioEvidenceInvocation.FacetProfile.VERIFICATION,
                    PortfolioEvidenceInvocation.FacetProfile.OUTCOME,
                    PortfolioEvidenceInvocation.FacetProfile.RESPONSIBILITY,
                    PortfolioEvidenceInvocation.FacetProfile.BACKGROUND,
                    PortfolioEvidenceInvocation.FacetProfile.LIMITATION);
            case MENTOR -> List.of(
                    PortfolioEvidenceInvocation.FacetProfile.TECHNICAL_DECISION,
                    PortfolioEvidenceInvocation.FacetProfile.LIMITATION,
                    PortfolioEvidenceInvocation.FacetProfile.IMPLEMENTATION,
                    PortfolioEvidenceInvocation.FacetProfile.VERIFICATION,
                    PortfolioEvidenceInvocation.FacetProfile.OUTCOME,
                    PortfolioEvidenceInvocation.FacetProfile.RESPONSIBILITY,
                    PortfolioEvidenceInvocation.FacetProfile.BACKGROUND);
            case HR -> List.of(
                    PortfolioEvidenceInvocation.FacetProfile.RESPONSIBILITY,
                    PortfolioEvidenceInvocation.FacetProfile.OUTCOME,
                    PortfolioEvidenceInvocation.FacetProfile.BACKGROUND,
                    PortfolioEvidenceInvocation.FacetProfile.IMPLEMENTATION,
                    PortfolioEvidenceInvocation.FacetProfile.VERIFICATION,
                    PortfolioEvidenceInvocation.FacetProfile.TECHNICAL_DECISION,
                    PortfolioEvidenceInvocation.FacetProfile.LIMITATION);
            case GUEST -> throw new IllegalStateException("guest priority is not reordered");
        };
        return facets.stream().sorted(java.util.Comparator.comparingInt(priority::indexOf))
                .toList();
    }

    /** Goal 层用户切面到检索切面的映射：OVERVIEW 按深度展开，SOLUTION 拆为实现+技术决策，STATUS 拆为结果+局限。 */
    private List<PortfolioEvidenceInvocation.FacetProfile> facets(
            UserGoalProposal.Facet facet, UserGoalProposal.Depth depth) {
        return switch (facet) {
            case OVERVIEW -> overview(depth);
            case BACKGROUND -> List.of(PortfolioEvidenceInvocation.FacetProfile.BACKGROUND);
            case RESPONSIBILITY -> List.of(PortfolioEvidenceInvocation.FacetProfile.RESPONSIBILITY);
            case SOLUTION -> List.of(
                    PortfolioEvidenceInvocation.FacetProfile.IMPLEMENTATION,
                    PortfolioEvidenceInvocation.FacetProfile.TECHNICAL_DECISION);
            case VERIFICATION -> List.of(PortfolioEvidenceInvocation.FacetProfile.VERIFICATION);
            case STATUS -> List.of(
                    PortfolioEvidenceInvocation.FacetProfile.OUTCOME,
                    PortfolioEvidenceInvocation.FacetProfile.LIMITATION);
        };
    }

    /** OVERVIEW 切面按深度展开：越深入覆盖越多侧面（简洁仅背景+结果，详细再补技术决策与局限）。 */
    private List<PortfolioEvidenceInvocation.FacetProfile> overview(
            UserGoalProposal.Depth depth) {
        return switch (depth) {
            case CONCISE -> List.of(
                    PortfolioEvidenceInvocation.FacetProfile.BACKGROUND,
                    PortfolioEvidenceInvocation.FacetProfile.OUTCOME);
            case STANDARD -> List.of(
                    PortfolioEvidenceInvocation.FacetProfile.BACKGROUND,
                    PortfolioEvidenceInvocation.FacetProfile.RESPONSIBILITY,
                    PortfolioEvidenceInvocation.FacetProfile.IMPLEMENTATION,
                    PortfolioEvidenceInvocation.FacetProfile.VERIFICATION,
                    PortfolioEvidenceInvocation.FacetProfile.OUTCOME);
            case DETAILED -> List.of(
                    PortfolioEvidenceInvocation.FacetProfile.BACKGROUND,
                    PortfolioEvidenceInvocation.FacetProfile.RESPONSIBILITY,
                    PortfolioEvidenceInvocation.FacetProfile.IMPLEMENTATION,
                    PortfolioEvidenceInvocation.FacetProfile.TECHNICAL_DECISION,
                    PortfolioEvidenceInvocation.FacetProfile.VERIFICATION,
                    PortfolioEvidenceInvocation.FacetProfile.OUTCOME,
                    PortfolioEvidenceInvocation.FacetProfile.LIMITATION);
        };
    }
}
