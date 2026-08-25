package com.portfolio.agent.turn.planning;

import java.util.Objects;

/**
 * 语义任务：计划中的最小执行单元。
 *
 * <p>类型决定 SourceDomain（作品集/通用/综合）；参数携带源目标类别、
 * 类型化参数、主体引用与受众画像；requestedOutputs 声明任务产出类别。</p>
 */
public final class SemanticTask {
    private final String taskId;
    private final Type type;
    private final SourceDomain sourceDomain;
    private final SemanticTaskParameters parameters;
    private final java.util.Set<GoalRequestedOutput> requestedOutputs;
    private final java.util.List<GoalSubjectReference> subjectReferences;

    private SemanticTask(
            String taskId,
            Type type,
            SourceDomain sourceDomain,
            SemanticTaskParameters parameters,
            java.util.Set<GoalRequestedOutput> requestedOutputs,
            java.util.List<GoalSubjectReference> subjectReferences) {
        this.taskId = UserGoal.requireId(taskId, "taskId");
        this.type = Objects.requireNonNull(type, "type");
        this.sourceDomain = Objects.requireNonNull(sourceDomain, "sourceDomain");
        this.parameters = Objects.requireNonNull(parameters, "parameters");
        this.requestedOutputs = java.util.Set.copyOf(requestedOutputs);
        this.subjectReferences = java.util.List.copyOf(subjectReferences);
    }

    /** 以空请求输出集合构造任务。 */
    public static SemanticTask of(String taskId, Type type, SemanticTaskParameters parameters) {
        return new SemanticTask(
                taskId, type, sourceDomain(type), parameters,
                java.util.Set.of(), parameters.getSubjects());
    }

    /** 以指定请求输出集合构造任务。 */
    public static SemanticTask of(
            String taskId,
            Type type,
            SemanticTaskParameters parameters,
            java.util.Set<GoalRequestedOutput> requestedOutputs) {
        return new SemanticTask(
                taskId, type, sourceDomain(type), parameters,
                requestedOutputs, parameters.getSubjects());
    }

    public String getTaskId() { return taskId; }
    public Type getType() { return type; }
    public SourceDomain getSourceDomain() { return sourceDomain; }
    public SemanticTaskParameters getParameters() { return parameters; }
    public java.util.Set<GoalRequestedOutput> getRequestedOutputs() { return requestedOutputs; }
    public java.util.List<GoalSubjectReference> getSubjectReferences() { return subjectReferences; }

    /** 任务类型到来源域的固定映射。 */
    private static SourceDomain sourceDomain(Type type) {
        return switch (type) {
            case PORTFOLIO_FACT, PORTFOLIO_COMPARE,
                    PORTFOLIO_RECOMMEND -> SourceDomain.PORTFOLIO;
            case GENERAL_EXPLANATION, GENERAL_COMPARISON -> SourceDomain.GENERAL;
            case CROSS_DOMAIN_SYNTHESIS -> SourceDomain.SYNTHESIS;
        };
    }

    /** 任务类型：作品集事实/比较/推荐、通用解释/比较、跨域综合。 */
    public enum Type {
        PORTFOLIO_FACT,
        PORTFOLIO_COMPARE,
        PORTFOLIO_RECOMMEND,
        GENERAL_EXPLANATION,
        GENERAL_COMPARISON,
        CROSS_DOMAIN_SYNTHESIS
    }

    /** 来源域：作品集证据、通用知识、跨域综合。 */
    public enum SourceDomain { PORTFOLIO, GENERAL, SYNTHESIS }
}
