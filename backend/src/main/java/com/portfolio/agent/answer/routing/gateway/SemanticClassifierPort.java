package com.portfolio.agent.answer.routing.gateway;

import com.portfolio.agent.answer.domain.ConversationModelFailureCode;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.ComparisonDimension;
import com.portfolio.agent.answer.routing.domain.PlanExclusion;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.ExclusionScope;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.ExclusionType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.RequestedOutput;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SemanticTaskType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskDependencyType;
import com.portfolio.agent.answer.routing.domain.SubjectReference;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Optional model boundary. Its output is only an untrusted closed candidate;
 * plan compilation and validation remain in the routing service.
 */
public interface SemanticClassifierPort {

    SemanticClassificationResult classify(SemanticClassificationInput input);

    final class SemanticClassificationInput {

        private final String question;
        private final List<SubjectReference> publicSubjects;

        public SemanticClassificationInput(String question, List<SubjectReference> publicSubjects) {
            if (question == null || question.isBlank()) {
                throw new IllegalArgumentException("question is required");
            }
            this.question = question.trim();
            this.publicSubjects = copyDistinct(publicSubjects, "publicSubjects");
        }

        public String getQuestion() {
            return question;
        }

        public List<SubjectReference> getPublicSubjects() {
            return publicSubjects;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SemanticClassificationInput that)) {
                return false;
            }
            return Objects.equals(question, that.question)
                    && Objects.equals(publicSubjects, that.publicSubjects);
        }

        @Override
        public int hashCode() {
            return Objects.hash(question, publicSubjects);
        }
    }

    final class SemanticClassificationResult {

        private final List<SemanticTaskCandidate> taskCandidates;
        private final List<DependencyCandidate> dependencyCandidates;
        private final List<ExclusionCandidate> exclusionCandidates;
        private final ConversationModelFailureCode failureCode;

        private SemanticClassificationResult(
                List<SemanticTaskCandidate> taskCandidates,
                List<DependencyCandidate> dependencyCandidates,
                List<ExclusionCandidate> exclusionCandidates,
                ConversationModelFailureCode failureCode) {
            this.taskCandidates = List.copyOf(Objects.requireNonNull(taskCandidates, "taskCandidates"));
            this.dependencyCandidates = List.copyOf(
                    Objects.requireNonNull(dependencyCandidates, "dependencyCandidates"));
            this.exclusionCandidates = List.copyOf(
                    Objects.requireNonNull(exclusionCandidates, "exclusionCandidates"));
            this.failureCode = failureCode;
            if (failureCode != null && (!this.taskCandidates.isEmpty()
                    || !this.dependencyCandidates.isEmpty() || !this.exclusionCandidates.isEmpty())) {
                throw new IllegalArgumentException("failed classification cannot carry candidates");
            }
        }

        public static SemanticClassificationResult success(
                List<SemanticTaskCandidate> taskCandidates,
                List<DependencyCandidate> dependencyCandidates,
                List<ExclusionCandidate> exclusionCandidates) {
            return new SemanticClassificationResult(
                    taskCandidates, dependencyCandidates, exclusionCandidates, null);
        }

        public static SemanticClassificationResult failure(ConversationModelFailureCode failureCode) {
            return new SemanticClassificationResult(List.of(), List.of(), List.of(),
                    Objects.requireNonNull(failureCode, "failureCode"));
        }

        public boolean isSuccessful() {
            return failureCode == null;
        }

        public List<SemanticTaskCandidate> getTaskCandidates() {
            return taskCandidates;
        }

        public List<DependencyCandidate> getDependencyCandidates() {
            return dependencyCandidates;
        }

        public List<ExclusionCandidate> getExclusionCandidates() {
            return exclusionCandidates;
        }

        public ConversationModelFailureCode getFailureCode() {
            return failureCode;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SemanticClassificationResult that)) {
                return false;
            }
            return Objects.equals(taskCandidates, that.taskCandidates)
                    && Objects.equals(dependencyCandidates, that.dependencyCandidates)
                    && Objects.equals(exclusionCandidates, that.exclusionCandidates)
                    && failureCode == that.failureCode;
        }

        @Override
        public int hashCode() {
            return Objects.hash(taskCandidates, dependencyCandidates, exclusionCandidates, failureCode);
        }
    }

    final class SemanticTaskCandidate {

        private final SemanticTaskType taskType;
        private final String questionSpan;
        private final List<SubjectReference> subjects;
        private final Set<ComparisonDimension> dimensions;
        private final Set<RequestedOutput> requestedOutputs;

        public SemanticTaskCandidate(
                SemanticTaskType taskType,
                String questionSpan,
                List<SubjectReference> subjects,
                Set<ComparisonDimension> dimensions,
                Set<RequestedOutput> requestedOutputs) {
            this.taskType = Objects.requireNonNull(taskType, "taskType");
            if (questionSpan == null || questionSpan.isBlank()) {
                throw new IllegalArgumentException("questionSpan is required");
            }
            this.questionSpan = questionSpan.trim();
            this.subjects = copyDistinct(subjects, "subjects");
            this.dimensions = Set.copyOf(Objects.requireNonNull(dimensions, "dimensions"));
            this.requestedOutputs = Set.copyOf(
                    Objects.requireNonNull(requestedOutputs, "requestedOutputs"));
        }

        public SemanticTaskType getTaskType() {
            return taskType;
        }

        public String getQuestionSpan() {
            return questionSpan;
        }

        public List<SubjectReference> getSubjects() {
            return subjects;
        }

        public Set<ComparisonDimension> getDimensions() {
            return dimensions;
        }

        public Set<RequestedOutput> getRequestedOutputs() {
            return requestedOutputs;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SemanticTaskCandidate that)) {
                return false;
            }
            return taskType == that.taskType
                    && Objects.equals(questionSpan, that.questionSpan)
                    && Objects.equals(subjects, that.subjects)
                    && Objects.equals(dimensions, that.dimensions)
                    && Objects.equals(requestedOutputs, that.requestedOutputs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(taskType, questionSpan, subjects, dimensions, requestedOutputs);
        }
    }

    final class DependencyCandidate {

        private final int fromTaskIndex;
        private final int toTaskIndex;
        private final TaskDependencyType dependencyType;

        public DependencyCandidate(int fromTaskIndex, int toTaskIndex, TaskDependencyType dependencyType) {
            if (fromTaskIndex < 0 || toTaskIndex < 0 || fromTaskIndex == toTaskIndex) {
                throw new IllegalArgumentException("dependency candidate indexes must be distinct non-negative values");
            }
            this.fromTaskIndex = fromTaskIndex;
            this.toTaskIndex = toTaskIndex;
            this.dependencyType = Objects.requireNonNull(dependencyType, "dependencyType");
        }

        public int getFromTaskIndex() {
            return fromTaskIndex;
        }

        public int getToTaskIndex() {
            return toTaskIndex;
        }

        public TaskDependencyType getDependencyType() {
            return dependencyType;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof DependencyCandidate that)) {
                return false;
            }
            return fromTaskIndex == that.fromTaskIndex
                    && toTaskIndex == that.toTaskIndex
                    && dependencyType == that.dependencyType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(fromTaskIndex, toTaskIndex, dependencyType);
        }
    }

    final class ExclusionCandidate {

        private final ExclusionScope scope;
        private final ExclusionType exclusionType;
        private final PlanExclusion.ExclusionValue controlledValue;
        private final Integer taskIndex;

        public ExclusionCandidate(
                ExclusionScope scope,
                ExclusionType exclusionType,
                PlanExclusion.ExclusionValue controlledValue,
                Integer taskIndex) {
            this.scope = Objects.requireNonNull(scope, "scope");
            this.exclusionType = Objects.requireNonNull(exclusionType, "exclusionType");
            this.controlledValue = Objects.requireNonNull(controlledValue, "controlledValue");
            this.taskIndex = taskIndex;
            if (scope == ExclusionScope.PLAN && taskIndex != null) {
                throw new IllegalArgumentException("plan exclusion cannot name a task index");
            }
            if (scope == ExclusionScope.TASK && (taskIndex == null || taskIndex < 0)) {
                throw new IllegalArgumentException("task exclusion requires a non-negative task index");
            }
            if (!matches(exclusionType, controlledValue)) {
                throw new IllegalArgumentException("controlledValue must match exclusionType");
            }
        }

        public ExclusionScope getScope() {
            return scope;
        }

        public ExclusionType getExclusionType() {
            return exclusionType;
        }

        public PlanExclusion.ExclusionValue getControlledValue() {
            return controlledValue;
        }

        public Integer getTaskIndex() {
            return taskIndex;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ExclusionCandidate that)) {
                return false;
            }
            return scope == that.scope
                    && exclusionType == that.exclusionType
                    && Objects.equals(controlledValue, that.controlledValue)
                    && Objects.equals(taskIndex, that.taskIndex);
        }

        @Override
        public int hashCode() {
            return Objects.hash(scope, exclusionType, controlledValue, taskIndex);
        }

        private static boolean matches(
                ExclusionType type, PlanExclusion.ExclusionValue value) {
            return switch (type) {
                case SUBJECT -> value instanceof PlanExclusion.SubjectValue;
                case OUTPUT -> value instanceof PlanExclusion.OutputValue;
                case DIMENSION -> value instanceof PlanExclusion.DimensionValue;
                case CONSTRAINT -> value instanceof PlanExclusion.ConstraintValue;
            };
        }
    }

    private static List<SubjectReference> copyDistinct(List<SubjectReference> values, String name) {
        List<SubjectReference> copied = List.copyOf(Objects.requireNonNull(values, name));
        if (new LinkedHashSet<>(copied).size() != copied.size()) {
            throw new IllegalArgumentException(name + " must not contain duplicates");
        }
        return copied;
    }
}
