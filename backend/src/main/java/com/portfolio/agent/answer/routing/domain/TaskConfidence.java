package com.portfolio.agent.answer.routing.domain;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.ConfidenceField;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.ConfidenceLevel;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.ConfidenceOrigin;

import java.util.Map;
import java.util.Objects;

public final class TaskConfidence {

    private final ConfidenceLevel overall;
    private final Map<ConfidenceField, ConfidenceLevel> fieldLevels;
    private final ConfidenceOrigin origin;

    public TaskConfidence(
            ConfidenceLevel overall,
            Map<ConfidenceField, ConfidenceLevel> fieldLevels,
            ConfidenceOrigin origin) {
        this.overall = Objects.requireNonNull(overall, "overall");
        this.fieldLevels = Map.copyOf(Objects.requireNonNull(fieldLevels, "fieldLevels"));
        for (Map.Entry<ConfidenceField, ConfidenceLevel> entry : this.fieldLevels.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "fieldLevels key");
            Objects.requireNonNull(entry.getValue(), "fieldLevels value");
        }
        this.origin = Objects.requireNonNull(origin, "origin");
    }

    public static TaskConfidence highRule() {
        return new TaskConfidence(ConfidenceLevel.HIGH, Map.of(), ConfidenceOrigin.RULE);
    }

    public ConfidenceLevel getOverall() {
        return overall;
    }

    public Map<ConfidenceField, ConfidenceLevel> getFieldLevels() {
        return fieldLevels;
    }

    public ConfidenceOrigin getOrigin() {
        return origin;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TaskConfidence that)) {
            return false;
        }
        return overall == that.overall
                && Objects.equals(fieldLevels, that.fieldLevels)
                && origin == that.origin;
    }

    @Override
    public int hashCode() {
        return Objects.hash(overall, fieldLevels, origin);
    }

    @Override
    public String toString() {
        return "TaskConfidence{overall=" + overall
                + ", origin=" + origin
                + ", fieldCount=" + fieldLevels.size() + '}';
    }
}
