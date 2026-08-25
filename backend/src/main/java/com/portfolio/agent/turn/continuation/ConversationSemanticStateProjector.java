package com.portfolio.agent.turn.continuation;

import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.SemanticTurnPlan;
import com.portfolio.agent.turn.planning.UserGoal;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import com.portfolio.agent.turn.projection.AnswerGoalResult;
import com.portfolio.agent.turn.projection.PublicAgentTurn;
import com.portfolio.agent.turn.projection.PublicPresentation;
import com.portfolio.agent.turn.projection.PublicSection;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Projects only backend-owned enums and public identifiers; no generated text survives.
 *
 * <p>会话语义状态投影器：从已产出的公开 Turn 与计划投影出
 * {@link ConversationSemanticState}。只保留枚举与公开 ID，
 * 任何生成文本都不会进入状态。</p>
 */
public final class ConversationSemanticStateProjector {

    /**
     * 投影本轮计划与公开 Turn 为语义状态。
     *
     * @return 非 Answer Turn、无有效覆盖目标或无可续接安全目标时返回 null
     *         （表示本轮不持久化语义状态）
     */
    public ConversationSemanticState project(
            SemanticTurnPlan plan, PublicAgentTurn turn, Instant updatedAt) {
        if (!(turn instanceof PublicAgentTurn.Answer answer)) return null;
        List<ConversationSemanticState.GoalSummary> goals = answer.getAnswer()
                .getGoalResults().stream()
                .filter(result -> result.getCoverage() != AnswerGoalResult.Coverage.NONE)
                .map(result -> goal(plan, result))
                .filter(ConversationSemanticState.GoalSummary::isPortfolioContinuationSafe)
                .toList();
        return goals.isEmpty() ? null : new ConversationSemanticState(
                plan.getContentReleaseId(), goals, updatedAt);
    }

    /** 从计划中的目标与任务参数构建目标摘要（剔除 RESULT 类别主体）。 */
    private ConversationSemanticState.GoalSummary goal(
            SemanticTurnPlan plan, AnswerGoalResult result) {
        UserGoal goal = plan.getUserGoals().stream()
                .filter(value -> value.getGoalId().equals(result.getGoalId()))
                .findFirst().orElseThrow();
        SemanticTask task = plan.findTask(goal.getFulfillmentTaskId()).orElseThrow();
        UserGoalProposal.GoalParameters parameters = task.getParameters().getParameters();
        Set<UserGoalProposal.Facet> facets = parameters
                instanceof UserGoalProposal.PortfolioFactParameters value
                ? value.getFacets() : Set.of();
        UserGoalProposal.Depth depth = switch (parameters) {
            case UserGoalProposal.PortfolioFactParameters value -> value.getDepth();
            case UserGoalProposal.GeneralExplanationParameters value -> value.getDepth();
            case UserGoalProposal.ApplyConceptParameters value -> value.getDepth();
            default -> null;
        };
        Set<UserGoalProposal.PortfolioComparisonDimension> dimensions = parameters
                instanceof UserGoalProposal.PortfolioCompareParameters value
                ? value.getDimensions() : Set.of();
        Integer requestedSize = parameters
                instanceof UserGoalProposal.PortfolioRecommendationParameters value
                ? value.getRequestedSize() : null;
        Set<String> constraints = parameters
                instanceof UserGoalProposal.PortfolioRecommendationParameters value
                ? value.getConstraints() : Set.of();
        return new ConversationSemanticState.GoalSummary(
                goal.getGoalId(), goal.getKind(), goal.getSubjects().stream()
                .filter(subject -> subject.getKind()
                        != com.portfolio.agent.turn.planning.GoalSubjectReference.Kind.RESULT)
                .map(subject -> new ConversationSemanticState.Subject(
                        subject.getKind(), subject.getReference())).distinct().toList(),
                goal.getRequestedOutputs(), facets, depth, dimensions,
                requestedSize, constraints, sections(result.getPresentation()));
    }

    /** 从分节或推荐展示中抽取小节引用列表。 */
    private List<ConversationSemanticState.SectionReference> sections(
            PublicPresentation presentation) {
        if (presentation instanceof PublicPresentation.Sectioned sectioned) {
            return sectioned.getSections().stream().map(this::section).toList();
        }
        if (presentation instanceof PublicPresentation.Recommendation recommendation) {
            return recommendation.getSupportingSections().stream()
                    .map(this::section).toList();
        }
        return List.of();
    }

    /** 把公开小节转换为小节引用。 */
    private ConversationSemanticState.SectionReference section(PublicSection section) {
        return new ConversationSemanticState.SectionReference(
                section.getSectionId(), section.getSectionKind());
    }
}
