package com.portfolio.agent.turn.planning;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 目标解析上下文：本轮解析允许引用的公开主体与目标类别范围。
 *
 * <p>由公开快照投影构建，供解释输入工厂、语义路由校验与计划编译共同
 * 使用；allowedRecommendationConstraints 界定推荐约束的公开目录。</p>
 */
public final class GoalResolutionContext {
    private final List<GoalInterpretationInput.PublicSubjectDescriptor> publicSubjects;
    private final Set<GoalKind> allowedGoalKinds;
    private final Set<String> allowedRecommendationConstraints;

    public GoalResolutionContext(
            List<GoalInterpretationInput.PublicSubjectDescriptor> publicSubjects,
            Set<GoalKind> allowedGoalKinds) {
        this(publicSubjects, allowedGoalKinds, Set.of());
    }

    public GoalResolutionContext(
            List<GoalInterpretationInput.PublicSubjectDescriptor> publicSubjects,
            Set<GoalKind> allowedGoalKinds,
            Set<String> allowedRecommendationConstraints) {
        this.publicSubjects = List.copyOf(Objects.requireNonNull(publicSubjects, "publicSubjects"));
        this.allowedGoalKinds = Set.copyOf(Objects.requireNonNull(allowedGoalKinds, "allowedGoalKinds"));
        this.allowedRecommendationConstraints = Set.copyOf(Objects.requireNonNull(
                allowedRecommendationConstraints, "allowedRecommendationConstraints"));
    }

    public List<GoalInterpretationInput.PublicSubjectDescriptor> getPublicSubjects() {
        return publicSubjects;
    }

    public Set<GoalKind> getAllowedGoalKinds() {
        return allowedGoalKinds;
    }

    public Set<String> getAllowedRecommendationConstraints() {
        return allowedRecommendationConstraints;
    }

    /** 判断主体提示是否指向某个公开主体；提示为 null 视为匹配。 */
    public boolean matchesHint(com.portfolio.agent.turn.lifecycle.AgentTurnCommand.SubjectHint hint) {
        return hint == null || resolveHint(hint) != null;
    }

    /**
     * 把主体提示解析为公开主体描述符。
     *
     * <p>按主体类别与已审核别名精确匹配公开目录；无匹配返回 null。</p>
     */
    public GoalInterpretationInput.PublicSubjectDescriptor resolveHint(
            com.portfolio.agent.turn.lifecycle.AgentTurnCommand.SubjectHint hint) {
        if (hint == null) return null;
        return publicSubjects.stream().filter(subject ->
                subject.getKind().name().equals(hint.getKind().name())
                        && subject.matchesAlias(hint.getSlug()))
                .findFirst().orElse(null);
    }
}
