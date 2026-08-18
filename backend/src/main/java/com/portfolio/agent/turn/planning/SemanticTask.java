package com.portfolio.agent.turn.planning;

import java.util.Objects;

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

    public static SemanticTask of(String taskId, Type type, SemanticTaskParameters parameters) {
        return new SemanticTask(
                taskId, type, sourceDomain(type), parameters,
                java.util.Set.of(), parameters.getSubjects());
    }

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

    private static SourceDomain sourceDomain(Type type) {
        return switch (type) {
            case PORTFOLIO_FACT, PORTFOLIO_COMPARE, PORTFOLIO_RECOMMEND,
                    PORTFOLIO_REFINE_RECOMMENDATION -> SourceDomain.PORTFOLIO;
            case GENERAL_EXPLANATION, GENERAL_COMPARISON -> SourceDomain.GENERAL;
            case CROSS_DOMAIN_SYNTHESIS -> SourceDomain.SYNTHESIS;
        };
    }

    public enum Type {
        PORTFOLIO_FACT,
        PORTFOLIO_COMPARE,
        PORTFOLIO_RECOMMEND,
        PORTFOLIO_REFINE_RECOMMENDATION,
        GENERAL_EXPLANATION,
        GENERAL_COMPARISON,
        CROSS_DOMAIN_SYNTHESIS
    }

    public enum SourceDomain { PORTFOLIO, GENERAL, SYNTHESIS }
}
