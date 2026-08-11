package com.portfolio.agent.answer.routing.domain;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.RequestedOutput;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SemanticTaskType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class SemanticTask {

    private final String taskId;
    private final SemanticTaskType taskType;
    private final TaskSourceDomain sourceDomain;
    private final String goalLabel;
    private final SemanticTaskParameters parameters;
    private final Set<RequestedOutput> requestedOutputs;
    private final TaskConfidence confidence;
    private final List<SubjectReference> subjectReferences;

    private SemanticTask(
            String taskId,
            SemanticTaskType taskType,
            TaskSourceDomain sourceDomain,
            String goalLabel,
            SemanticTaskParameters parameters,
            Set<RequestedOutput> requestedOutputs,
            TaskConfidence confidence,
            List<SubjectReference> subjectReferences) {
        this.taskId = requireText(taskId, "taskId");
        this.taskType = Objects.requireNonNull(taskType, "taskType");
        this.sourceDomain = Objects.requireNonNull(sourceDomain, "sourceDomain");
        this.goalLabel = requireText(goalLabel, "goalLabel");
        this.parameters = Objects.requireNonNull(parameters, "parameters");
        this.requestedOutputs = Set.copyOf(Objects.requireNonNull(requestedOutputs, "requestedOutputs"));
        this.confidence = Objects.requireNonNull(confidence, "confidence");
        this.subjectReferences = List.copyOf(Objects.requireNonNull(subjectReferences, "subjectReferences"));
        validateMatrix();
        validateSubjectReferences();
    }

    public static SemanticTask create(
            String taskId,
            SemanticTaskType taskType,
            TaskSourceDomain sourceDomain,
            String goalLabel,
            SemanticTaskParameters parameters,
            Set<RequestedOutput> requestedOutputs,
            TaskConfidence confidence,
            List<SubjectReference> subjectReferences) {
        return new SemanticTask(
                taskId, taskType, sourceDomain, goalLabel, parameters,
                requestedOutputs, confidence, subjectReferences);
    }

    public static SemanticTask portfolioCompare(
            String taskId, String goalLabel, SemanticTaskParameters.PortfolioCompare parameters) {
        Objects.requireNonNull(parameters, "parameters");
        return create(
                taskId,
                SemanticTaskType.PORTFOLIO_COMPARE,
                TaskSourceDomain.PORTFOLIO,
                goalLabel,
                parameters,
                Set.of(RequestedOutput.SUMMARY, RequestedOutput.COMPARISON),
                TaskConfidence.highRule(),
                parameters.getSubjects());
    }

    public String getTaskId() {
        return taskId;
    }

    public SemanticTaskType getTaskType() {
        return taskType;
    }

    public TaskSourceDomain getSourceDomain() {
        return sourceDomain;
    }

    public String getGoalLabel() {
        return goalLabel;
    }

    public SemanticTaskParameters getParameters() {
        return parameters;
    }

    public Set<RequestedOutput> getRequestedOutputs() {
        return requestedOutputs;
    }

    public TaskConfidence getConfidence() {
        return confidence;
    }

    public List<SubjectReference> getSubjectReferences() {
        return subjectReferences;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SemanticTask that)) {
            return false;
        }
        return Objects.equals(taskId, that.taskId)
                && taskType == that.taskType
                && sourceDomain == that.sourceDomain
                && Objects.equals(goalLabel, that.goalLabel)
                && Objects.equals(parameters, that.parameters)
                && Objects.equals(requestedOutputs, that.requestedOutputs)
                && Objects.equals(confidence, that.confidence)
                && Objects.equals(subjectReferences, that.subjectReferences);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId, taskType, sourceDomain, goalLabel, parameters,
                requestedOutputs, confidence, subjectReferences);
    }

    @Override
    public String toString() {
        return "SemanticTask{taskType=" + taskType
                + ", sourceDomain=" + sourceDomain
                + ", parameterType=" + parameters.getClass().getSimpleName()
                + ", requestedOutputCount=" + requestedOutputs.size()
                + ", confidence=" + confidence
                + ", subjectCount=" + subjectReferences.size() + '}';
    }

    private void validateMatrix() {
        boolean valid = switch (taskType) {
            case PORTFOLIO_FACT -> sourceDomain == TaskSourceDomain.PORTFOLIO
                    && parameters instanceof SemanticTaskParameters.PortfolioFact;
            case PORTFOLIO_COMPARE -> sourceDomain == TaskSourceDomain.PORTFOLIO
                    && parameters instanceof SemanticTaskParameters.PortfolioCompare;
            case PORTFOLIO_RECOMMEND -> sourceDomain == TaskSourceDomain.PORTFOLIO
                    && parameters instanceof SemanticTaskParameters.PortfolioRecommend;
            case PORTFOLIO_REFINE_RECOMMENDATION -> sourceDomain == TaskSourceDomain.PORTFOLIO
                    && parameters instanceof SemanticTaskParameters.PortfolioRefinement;
            case GENERAL_EXPLANATION -> sourceDomain == TaskSourceDomain.GENERAL
                    && parameters instanceof SemanticTaskParameters.GeneralExplanation;
            case GENERAL_COMPARISON -> sourceDomain == TaskSourceDomain.GENERAL
                    && parameters instanceof SemanticTaskParameters.GeneralComparison;
            case SYNTHESIS -> sourceDomain == TaskSourceDomain.SYNTHESIS
                    && parameters instanceof SemanticTaskParameters.Synthesis synthesis
                    && synthesis.getSourceTaskIds().size() >= 2;
        };
        if (!valid) {
            throw new IllegalArgumentException("taskType, sourceDomain, and parameters must form a supported matrix");
        }
    }

    private void validateSubjectReferences() {
        Set<SubjectReference> actual = new LinkedHashSet<>(subjectReferences);
        if (actual.size() != subjectReferences.size()
                || !actual.equals(expectedSubjectReferences(parameters))) {
            throw new IllegalArgumentException("subjectReferences must match typed task parameters");
        }
    }

    private static Set<SubjectReference> expectedSubjectReferences(SemanticTaskParameters taskParameters) {
        if (taskParameters instanceof SemanticTaskParameters.PortfolioFact fact) {
            return Set.of(fact.getSubject());
        }
        if (taskParameters instanceof SemanticTaskParameters.PortfolioCompare comparison) {
            return Set.copyOf(comparison.getSubjects());
        }
        if (taskParameters instanceof SemanticTaskParameters.PortfolioRecommend recommendation) {
            return Set.copyOf(recommendation.getCandidateSubjects());
        }
        if (taskParameters instanceof SemanticTaskParameters.PortfolioRefinement refinement) {
            Set<SubjectReference> references = new LinkedHashSet<>();
            references.add(refinement.getBaseResultReference());
            references.addAll(refinement.getRemovedSubjects());
            return Set.copyOf(references);
        }
        return Set.of();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
