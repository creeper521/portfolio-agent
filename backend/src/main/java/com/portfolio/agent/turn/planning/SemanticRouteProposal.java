package com.portfolio.agent.turn.planning;

import java.util.Objects;
import java.util.Optional;

/**
 * Model-proposed closed semantics. This value never carries state handles,
 * tokens, tasks, providers or evidence.
 *
 * <p>语义路由提案：模型在 Goal 解释阶段提出的封闭路由决定。仅携带路由
 * 类别、候选键、目标提案、澄清提案或最近语义引用；构造时按路由校验字段
 * 形状（validateShape），保证每条路由只允许对应的载荷组合。</p>
 */
public final class SemanticRouteProposal {
    private final Route route;
    private final String candidateKey;
    private final UserGoalProposal goalProposal;
    private final ClarificationProposal clarification;
    private final RecentSemanticReference recentReference;

    private SemanticRouteProposal(
            Route route,
            String candidateKey,
            UserGoalProposal goalProposal,
            ClarificationProposal clarification,
            RecentSemanticReference recentReference) {
        this.route = Objects.requireNonNull(route, "route");
        this.candidateKey = candidateKey == null
                ? null : requireCandidateKey(candidateKey);
        this.goalProposal = goalProposal;
        this.clarification = clarification;
        this.recentReference = recentReference;
        validateShape();
    }

    public static SemanticRouteProposal standardGoal(UserGoalProposal goalProposal) {
        return new SemanticRouteProposal(
                Route.STANDARD_GOAL, null,
                Objects.requireNonNull(goalProposal, "goalProposal"), null, null);
    }

    public static SemanticRouteProposal standardGoal(
            UserGoalProposal goalProposal,
            RecentSemanticReference recentReference) {
        return new SemanticRouteProposal(
                Route.STANDARD_GOAL, null,
                Objects.requireNonNull(goalProposal, "goalProposal"), null,
                Objects.requireNonNull(recentReference, "recentReference"));
    }

    public static SemanticRouteProposal needsClarification(
            ClarificationProposal clarification) {
        return new SemanticRouteProposal(
                Route.NEEDS_CLARIFICATION, null, null,
                Objects.requireNonNull(clarification, "clarification"), null);
    }

    public static SemanticRouteProposal needsClarification() {
        return new SemanticRouteProposal(
                Route.NEEDS_CLARIFICATION, null, null, null, null);
    }

    public static SemanticRouteProposal enterRecommendedResult(String candidateKey) {
        return new SemanticRouteProposal(
                Route.ENTER_RECOMMENDED_RESULT, candidateKey, null, null, null);
    }

    public static SemanticRouteProposal discussion(
            Route route, String candidateKey, UserGoalProposal goalProposal) {
        return new SemanticRouteProposal(route, candidateKey, goalProposal, null, null);
    }

    /** 构造仅含状态的路由（START_NEW_TOPIC / REENTER_PROJECT），其余路由拒绝。 */
    public static SemanticRouteProposal stateRoute(Route route) {
        if (route != Route.START_NEW_TOPIC
                && route != Route.REENTER_PROJECT) {
            throw new IllegalArgumentException(
                    "route is not a state-only route");
        }
        return new SemanticRouteProposal(route, null, null, null, null);
    }

    public Route getRoute() { return route; }
    public Optional<String> getCandidateKey() {
        return Optional.ofNullable(candidateKey);
    }
    public Optional<UserGoalProposal> getGoalProposal() {
        return Optional.ofNullable(goalProposal);
    }
    public Optional<ClarificationProposal> getClarification() {
        return Optional.ofNullable(clarification);
    }
    public Optional<RecentSemanticReference> getRecentReference() {
        return Optional.ofNullable(recentReference);
    }

    /** 按路由类别校验载荷形状组合，越界组合直接拒绝。 */
    private void validateShape() {
        switch (route) {
            case STANDARD_GOAL -> require(
                    goalProposal != null && candidateKey == null && clarification == null);
            case ENTER_RECOMMENDED_RESULT, SWITCH_PROJECT -> require(
                    candidateKey != null && goalProposal == null && clarification == null
                            && recentReference == null);
            case NEEDS_CLARIFICATION -> require(
                    candidateKey == null && goalProposal == null && recentReference == null);
            case CONTINUE_CURRENT_PROJECT -> require(
                    candidateKey == null && goalProposal != null && clarification == null
                            && recentReference == null);
            case START_NEW_TOPIC, REENTER_PROJECT -> require(
                    candidateKey == null && goalProposal == null && clarification == null
                            && recentReference == null);
        }
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new IllegalArgumentException(
                    "semantic route fields do not match route");
        }
    }

    /** 候选键必须是 C1..C5。 */
    private static String requireCandidateKey(String value) {
        if (!value.matches("C[1-5]")) {
            throw new IllegalArgumentException("candidate key is invalid");
        }
        return value;
    }

    /**
     * 最近语义引用：指向最近成功 Turn 的某个 goal（可选进一步定位到某个 section），
     * 用于无主体目标的"最近主体"续接绑定。
     */
    public record RecentSemanticReference(String goalId, String sectionId) {
        public RecentSemanticReference {
            goalId = requireSemanticId(goalId, "goalId");
            if (sectionId != null) {
                sectionId = requireSemanticId(sectionId, "sectionId");
            }
        }

        private static String requireSemanticId(String value, String name) {
            if (value == null || !value.matches("[a-z0-9][a-z0-9-]{1,95}")) {
                throw new IllegalArgumentException(name + " is invalid");
            }
            return value;
        }
    }

    /** 路由类别：标准目标/进入推荐结果/继续当前项目/开新话题/切换项目/重入项目/需要澄清。 */
    public enum Route {
        STANDARD_GOAL,
        ENTER_RECOMMENDED_RESULT,
        CONTINUE_CURRENT_PROJECT,
        START_NEW_TOPIC,
        SWITCH_PROJECT,
        REENTER_PROJECT,
        NEEDS_CLARIFICATION
    }
}
