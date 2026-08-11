package com.portfolio.agent.answer.routing.adapter.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.ConversationModelFailureCode;
import com.portfolio.agent.answer.routing.domain.PlanExclusion;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.ComparisonDimension;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.ConstraintCode;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.ExclusionScope;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.ExclusionType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.RequestedOutput;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SemanticTaskType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectResolutionSource;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskDependencyType;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.gateway.SemanticClassifierPort;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Decodes a provider payload into a closed candidate, never into a plan. */
public final class SemanticClassificationCodec {

    private final ObjectMapper objectMapper;

    public SemanticClassificationCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper").copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public SemanticClassifierPort.SemanticClassificationResult decode(
            String payload,
            SemanticClassifierPort.SemanticClassificationInput input) {
        try {
            if (payload == null) {
                throw new IllegalArgumentException("payload is required");
            }
            return decode(objectMapper.readValue(payload, WireResponse.class), input);
        } catch (Exception exception) {
            return SemanticClassifierPort.SemanticClassificationResult.failure(
                    ConversationModelFailureCode.INVALID_RESPONSE);
        }
    }

    SemanticClassifierPort.SemanticClassificationResult decode(
            WireResponse response,
            SemanticClassifierPort.SemanticClassificationInput input) {
        Objects.requireNonNull(input, "input");
        try {
            if (response == null || response.getTaskCandidates() == null
                    || response.getDependencyCandidates() == null
                    || response.getExclusionCandidates() == null) {
                throw new IllegalArgumentException("candidate response must contain all fields");
            }
            List<SemanticClassifierPort.SemanticTaskCandidate> taskCandidates = new ArrayList<>();
            for (WireTaskCandidate candidate : response.getTaskCandidates()) {
                taskCandidates.add(taskCandidate(candidate, input));
            }
            List<SemanticClassifierPort.DependencyCandidate> dependencies = new ArrayList<>();
            for (WireDependencyCandidate candidate : response.getDependencyCandidates()) {
                dependencies.add(dependencyCandidate(candidate, taskCandidates.size()));
            }
            List<SemanticClassifierPort.ExclusionCandidate> exclusions = new ArrayList<>();
            for (WireExclusionCandidate candidate : response.getExclusionCandidates()) {
                exclusions.add(exclusionCandidate(candidate, taskCandidates.size(), input));
            }
            return SemanticClassifierPort.SemanticClassificationResult.success(
                    taskCandidates, dependencies, exclusions);
        } catch (RuntimeException exception) {
            return SemanticClassifierPort.SemanticClassificationResult.failure(
                    ConversationModelFailureCode.INVALID_RESPONSE);
        }
    }

    private SemanticClassifierPort.SemanticTaskCandidate taskCandidate(
            WireTaskCandidate candidate,
            SemanticClassifierPort.SemanticClassificationInput input) {
        if (candidate == null || !isCurrentQuestionSpan(candidate.getQuestionSpan(), input.getQuestion())) {
            throw new IllegalArgumentException("candidate must name a current-question span");
        }
        SemanticTaskType taskType = parseEnum(
                SemanticTaskType.class, candidate.getTaskType(), "taskType");
        List<SubjectReference> subjects = subjects(candidate.getSubjects(), input.getPublicSubjects());
        Set<ComparisonDimension> dimensions = parseEnums(
                ComparisonDimension.class, candidate.getDimensions(), "dimensions");
        Set<RequestedOutput> requestedOutputs = parseEnums(
                RequestedOutput.class, candidate.getRequestedOutputs(), "requestedOutputs");
        validateTaskSubjects(taskType, subjects);
        return new SemanticClassifierPort.SemanticTaskCandidate(
                taskType, candidate.getQuestionSpan(), subjects, dimensions, requestedOutputs);
    }

    private List<SubjectReference> subjects(
            List<WireSubject> candidates,
            List<SubjectReference> publicSubjects) {
        if (candidates == null) {
            throw new IllegalArgumentException("subjects is required");
        }
        List<SubjectReference> subjects = new ArrayList<>();
        for (WireSubject candidate : candidates) {
            if (candidate == null) {
                throw new IllegalArgumentException("subjects cannot contain null");
            }
            SubjectType subjectType = parseEnum(
                    SubjectType.class, candidate.getSubjectType(), "subjectType");
            Optional<SubjectReference> matched = publicSubjects.stream()
                    .filter(subject -> subject.getSubjectType() == subjectType
                            && subject.getSubjectId().equals(candidate.getSubjectId()))
                    .findFirst();
            if (matched.isEmpty()) {
                throw new IllegalArgumentException("subject must appear in the public catalog");
            }
            SubjectReference subject = matched.orElseThrow();
            subjects.add(new SubjectReference(
                    subject.getSubjectType(), subject.getSubjectId(),
                    SubjectResolutionSource.VALIDATED_MODEL_CANDIDATE,
                    subject.getContentVersion()));
        }
        if (new LinkedHashSet<>(subjects).size() != subjects.size()) {
            throw new IllegalArgumentException("subjects must not contain duplicates");
        }
        return List.copyOf(subjects);
    }

    private SemanticClassifierPort.DependencyCandidate dependencyCandidate(
            WireDependencyCandidate candidate,
            int taskCandidateCount) {
        if (candidate == null) {
            throw new IllegalArgumentException("dependency candidate is required");
        }
        int fromTaskIndex = candidate.getFromTaskIndex();
        int toTaskIndex = candidate.getToTaskIndex();
        if (fromTaskIndex >= taskCandidateCount || toTaskIndex >= taskCandidateCount) {
            throw new IllegalArgumentException("dependency candidate index is outside the candidate set");
        }
        return new SemanticClassifierPort.DependencyCandidate(
                fromTaskIndex, toTaskIndex,
                parseEnum(TaskDependencyType.class, candidate.getDependencyType(), "dependencyType"));
    }

    private SemanticClassifierPort.ExclusionCandidate exclusionCandidate(
            WireExclusionCandidate candidate,
            int taskCandidateCount,
            SemanticClassifierPort.SemanticClassificationInput input) {
        if (candidate == null) {
            throw new IllegalArgumentException("exclusion candidate is required");
        }
        ExclusionScope scope = parseEnum(ExclusionScope.class, candidate.getScope(), "scope");
        Integer taskIndex = candidate.getTaskIndex();
        if (taskIndex != null && taskIndex >= taskCandidateCount) {
            throw new IllegalArgumentException("exclusion candidate index is outside the candidate set");
        }
        ExclusionType exclusionType = parseEnum(
                ExclusionType.class, candidate.getExclusionType(), "exclusionType");
        return new SemanticClassifierPort.ExclusionCandidate(
                scope, exclusionType, controlledValue(candidate, exclusionType, input), taskIndex);
    }

    private PlanExclusion.ExclusionValue controlledValue(
            WireExclusionCandidate candidate,
            ExclusionType exclusionType,
            SemanticClassifierPort.SemanticClassificationInput input) {
        boolean hasSubject = candidate.isSubjectPresent();
        boolean hasOutput = candidate.isRequestedOutputPresent();
        boolean hasDimension = candidate.isDimensionPresent();
        boolean hasConstraint = candidate.isConstraintPresent();
        return switch (exclusionType) {
            case SUBJECT -> {
                requireOnly(hasSubject, !hasOutput && !hasDimension && !hasConstraint,
                        "subject");
                yield new PlanExclusion.SubjectValue(
                        subject(candidate.getSubject(), input.getPublicSubjects()));
            }
            case OUTPUT -> {
                requireOnly(hasOutput, !hasSubject && !hasDimension && !hasConstraint,
                        "requestedOutput");
                yield new PlanExclusion.OutputValue(
                        parseEnum(RequestedOutput.class, candidate.getRequestedOutput(), "requestedOutput"));
            }
            case DIMENSION -> {
                requireOnly(hasDimension, !hasSubject && !hasOutput && !hasConstraint,
                        "dimension");
                yield new PlanExclusion.DimensionValue(
                        parseEnum(ComparisonDimension.class, candidate.getDimension(), "dimension"));
            }
            case CONSTRAINT -> {
                requireOnly(hasConstraint, !hasSubject && !hasOutput && !hasDimension,
                        "constraint");
                yield new PlanExclusion.ConstraintValue(
                        parseEnum(ConstraintCode.class, candidate.getConstraint(), "constraint"));
            }
        };
    }

    private void requireOnly(boolean matchingValue, boolean noOtherValue, String fieldName) {
        if (!matchingValue || !noOtherValue) {
            throw new IllegalArgumentException(
                    "exclusion candidate must contain only " + fieldName);
        }
    }

    private SubjectReference subject(
            WireSubject candidate, List<SubjectReference> publicSubjects) {
        if (candidate == null) {
            throw new IllegalArgumentException("subject is required");
        }
        SubjectType subjectType = parseEnum(
                SubjectType.class, candidate.getSubjectType(), "subjectType");
        Optional<SubjectReference> matched = publicSubjects.stream()
                .filter(subject -> subject.getSubjectType() == subjectType
                        && subject.getSubjectId().equals(candidate.getSubjectId()))
                .findFirst();
        if (matched.isEmpty()) {
            throw new IllegalArgumentException("subject must appear in the public catalog");
        }
        SubjectReference value = matched.orElseThrow();
        return new SubjectReference(
                value.getSubjectType(), value.getSubjectId(),
                SubjectResolutionSource.VALIDATED_MODEL_CANDIDATE,
                value.getContentVersion());
    }

    private void validateTaskSubjects(SemanticTaskType taskType, List<SubjectReference> subjects) {
        if (taskType == SemanticTaskType.PORTFOLIO_FACT && subjects.size() != 1) {
            throw new IllegalArgumentException("portfolio fact candidate requires exactly one public subject");
        }
        if (taskType == SemanticTaskType.PORTFOLIO_COMPARE
                && (subjects.size() < 2 || subjects.size() > 3)) {
            throw new IllegalArgumentException("portfolio comparison candidate requires two or three public subjects");
        }
        if ((taskType == SemanticTaskType.GENERAL_EXPLANATION
                || taskType == SemanticTaskType.GENERAL_COMPARISON
                || taskType == SemanticTaskType.SYNTHESIS) && !subjects.isEmpty()) {
            throw new IllegalArgumentException("non-portfolio candidate cannot carry public subjects");
        }
    }

    private boolean isCurrentQuestionSpan(String span, String question) {
        return span != null && !span.isBlank() && question.contains(span.trim());
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        try {
            return Enum.valueOf(type, value.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(name + " is not supported", exception);
        }
    }

    private <E extends Enum<E>> Set<E> parseEnums(
            Class<E> type,
            List<String> values,
            String name) {
        if (values == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        Set<E> parsed = new LinkedHashSet<>();
        for (String value : values) {
            parsed.add(parseEnum(type, value, name));
        }
        return Set.copyOf(parsed);
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    static final class WireResponse {

        private List<WireTaskCandidate> taskCandidates;
        private List<WireDependencyCandidate> dependencyCandidates;
        private List<WireExclusionCandidate> exclusionCandidates;

        public List<WireTaskCandidate> getTaskCandidates() {
            return taskCandidates;
        }

        public void setTaskCandidates(List<WireTaskCandidate> taskCandidates) {
            this.taskCandidates = taskCandidates;
        }

        public List<WireDependencyCandidate> getDependencyCandidates() {
            return dependencyCandidates;
        }

        public void setDependencyCandidates(List<WireDependencyCandidate> dependencyCandidates) {
            this.dependencyCandidates = dependencyCandidates;
        }

        public List<WireExclusionCandidate> getExclusionCandidates() {
            return exclusionCandidates;
        }

        public void setExclusionCandidates(List<WireExclusionCandidate> exclusionCandidates) {
            this.exclusionCandidates = exclusionCandidates;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    static final class WireTaskCandidate {

        private String taskType;
        private String questionSpan;
        private List<WireSubject> subjects;
        private List<String> dimensions;
        private List<String> requestedOutputs;

        public String getTaskType() { return taskType; }
        public void setTaskType(String taskType) { this.taskType = taskType; }
        public String getQuestionSpan() { return questionSpan; }
        public void setQuestionSpan(String questionSpan) { this.questionSpan = questionSpan; }
        public List<WireSubject> getSubjects() { return subjects; }
        public void setSubjects(List<WireSubject> subjects) { this.subjects = subjects; }
        public List<String> getDimensions() { return dimensions; }
        public void setDimensions(List<String> dimensions) { this.dimensions = dimensions; }
        public List<String> getRequestedOutputs() { return requestedOutputs; }
        public void setRequestedOutputs(List<String> requestedOutputs) {
            this.requestedOutputs = requestedOutputs;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    static final class WireSubject {

        private String subjectType;
        private String subjectId;

        public String getSubjectType() { return subjectType; }
        public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
        public String getSubjectId() { return subjectId; }
        public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    static final class WireDependencyCandidate {

        private int fromTaskIndex;
        private int toTaskIndex;
        private String dependencyType;

        public int getFromTaskIndex() { return fromTaskIndex; }
        public void setFromTaskIndex(int fromTaskIndex) { this.fromTaskIndex = fromTaskIndex; }
        public int getToTaskIndex() { return toTaskIndex; }
        public void setToTaskIndex(int toTaskIndex) { this.toTaskIndex = toTaskIndex; }
        public String getDependencyType() { return dependencyType; }
        public void setDependencyType(String dependencyType) { this.dependencyType = dependencyType; }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    static final class WireExclusionCandidate {

        private String scope;
        private String exclusionType;
        private String requestedOutput;
        private String dimension;
        private String constraint;
        private Integer taskIndex;
        private WireSubject subject;
        private boolean requestedOutputPresent;
        private boolean dimensionPresent;
        private boolean constraintPresent;
        private boolean subjectPresent;

        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
        public String getExclusionType() { return exclusionType; }
        public void setExclusionType(String exclusionType) { this.exclusionType = exclusionType; }
        public String getRequestedOutput() { return requestedOutput; }
        public void setRequestedOutput(String requestedOutput) {
            this.requestedOutputPresent = true;
            this.requestedOutput = requestedOutput;
        }
        public String getDimension() { return dimension; }
        public void setDimension(String dimension) {
            this.dimensionPresent = true;
            this.dimension = dimension;
        }
        public String getConstraint() { return constraint; }
        public void setConstraint(String constraint) {
            this.constraintPresent = true;
            this.constraint = constraint;
        }
        public Integer getTaskIndex() { return taskIndex; }
        public void setTaskIndex(Integer taskIndex) { this.taskIndex = taskIndex; }
        public WireSubject getSubject() { return subject; }
        public void setSubject(WireSubject subject) {
            this.subjectPresent = true;
            this.subject = subject;
        }
        public boolean isRequestedOutputPresent() { return requestedOutputPresent; }
        public boolean isDimensionPresent() { return dimensionPresent; }
        public boolean isConstraintPresent() { return constraintPresent; }
        public boolean isSubjectPresent() { return subjectPresent; }
    }
}
