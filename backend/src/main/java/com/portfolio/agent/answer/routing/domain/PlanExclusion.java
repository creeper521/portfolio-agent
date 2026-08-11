package com.portfolio.agent.answer.routing.domain;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.ComparisonDimension;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.ConstraintCode;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.ExclusionScope;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.ExclusionType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.RequestedOutput;

import java.util.Objects;

public final class PlanExclusion {

    private final ExclusionScope scope;
    private final ExclusionType type;
    private final String taskId;
    private final ExclusionValue controlledValue;

    public PlanExclusion(
            ExclusionScope scope, ExclusionType type, String taskId, ExclusionValue controlledValue) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.type = Objects.requireNonNull(type, "type");
        this.taskId = normalizeText(taskId);
        this.controlledValue = Objects.requireNonNull(controlledValue, "controlledValue");
        if (scope == ExclusionScope.TASK && this.taskId == null) {
            throw new IllegalArgumentException("taskId is required for a task exclusion");
        }
        if (scope == ExclusionScope.PLAN && this.taskId != null) {
            throw new IllegalArgumentException("taskId is not allowed for a plan exclusion");
        }
        if (!matches(type, controlledValue)) {
            throw new IllegalArgumentException("controlledValue must match exclusion type");
        }
    }

    public static PlanExclusion planOutput(RequestedOutput output) {
        return new PlanExclusion(
                ExclusionScope.PLAN, ExclusionType.OUTPUT, null, new OutputValue(output));
    }

    public static PlanExclusion taskOutput(String taskId, RequestedOutput output) {
        return new PlanExclusion(
                ExclusionScope.TASK, ExclusionType.OUTPUT, taskId, new OutputValue(output));
    }

    public static PlanExclusion planSubject(SubjectReference subject) {
        return new PlanExclusion(
                ExclusionScope.PLAN, ExclusionType.SUBJECT, null, new SubjectValue(subject));
    }

    public static PlanExclusion taskSubject(String taskId, SubjectReference subject) {
        return new PlanExclusion(
                ExclusionScope.TASK, ExclusionType.SUBJECT, taskId, new SubjectValue(subject));
    }

    public static PlanExclusion planDimension(ComparisonDimension dimension) {
        return new PlanExclusion(
                ExclusionScope.PLAN, ExclusionType.DIMENSION, null, new DimensionValue(dimension));
    }

    public static PlanExclusion taskDimension(String taskId, ComparisonDimension dimension) {
        return new PlanExclusion(
                ExclusionScope.TASK, ExclusionType.DIMENSION, taskId, new DimensionValue(dimension));
    }

    public static PlanExclusion planConstraint(ConstraintCode constraint) {
        return new PlanExclusion(
                ExclusionScope.PLAN, ExclusionType.CONSTRAINT, null, new ConstraintValue(constraint));
    }

    public static PlanExclusion taskConstraint(String taskId, ConstraintCode constraint) {
        return new PlanExclusion(
                ExclusionScope.TASK, ExclusionType.CONSTRAINT, taskId, new ConstraintValue(constraint));
    }

    public ExclusionScope getScope() {
        return scope;
    }

    public ExclusionType getType() {
        return type;
    }

    public String getTaskId() {
        return taskId;
    }

    public ExclusionValue getControlledValue() {
        return controlledValue;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PlanExclusion that)) {
            return false;
        }
        return scope == that.scope
                && type == that.type
                && Objects.equals(taskId, that.taskId)
                && Objects.equals(controlledValue, that.controlledValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scope, type, taskId, controlledValue);
    }

    @Override
    public String toString() {
        return "PlanExclusion{scope=" + scope + ", type=" + type + '}';
    }

    public interface ExclusionValue {
    }

    public static final class SubjectValue implements ExclusionValue {

        private final SubjectReference subject;

        public SubjectValue(SubjectReference subject) {
            this.subject = Objects.requireNonNull(subject, "subject");
        }

        public SubjectReference getSubject() {
            return subject;
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof SubjectValue that
                    && Objects.equals(subject, that.subject);
        }

        @Override
        public int hashCode() {
            return Objects.hash(subject);
        }

        @Override
        public String toString() {
            return "SubjectValue{redacted}";
        }
    }

    public static final class OutputValue implements ExclusionValue {

        private final RequestedOutput output;

        public OutputValue(RequestedOutput output) {
            this.output = Objects.requireNonNull(output, "output");
        }

        public RequestedOutput getOutput() {
            return output;
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof OutputValue that && output == that.output;
        }

        @Override
        public int hashCode() {
            return Objects.hash(output);
        }

        @Override
        public String toString() {
            return "OutputValue{redacted}";
        }
    }

    public static final class DimensionValue implements ExclusionValue {

        private final ComparisonDimension dimension;

        public DimensionValue(ComparisonDimension dimension) {
            this.dimension = Objects.requireNonNull(dimension, "dimension");
        }

        public ComparisonDimension getDimension() {
            return dimension;
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof DimensionValue that && dimension == that.dimension;
        }

        @Override
        public int hashCode() {
            return Objects.hash(dimension);
        }

        @Override
        public String toString() {
            return "DimensionValue{redacted}";
        }
    }

    public static final class ConstraintValue implements ExclusionValue {

        private final ConstraintCode constraint;

        public ConstraintValue(ConstraintCode constraint) {
            this.constraint = Objects.requireNonNull(constraint, "constraint");
        }

        public ConstraintCode getConstraint() {
            return constraint;
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof ConstraintValue that && constraint == that.constraint;
        }

        @Override
        public int hashCode() {
            return Objects.hash(constraint);
        }

        @Override
        public String toString() {
            return "ConstraintValue{redacted}";
        }
    }

    private static boolean matches(ExclusionType type, ExclusionValue value) {
        return switch (type) {
            case SUBJECT -> value instanceof SubjectValue;
            case OUTPUT -> value instanceof OutputValue;
            case DIMENSION -> value instanceof DimensionValue;
            case CONSTRAINT -> value instanceof ConstraintValue;
        };
    }

    private static String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
