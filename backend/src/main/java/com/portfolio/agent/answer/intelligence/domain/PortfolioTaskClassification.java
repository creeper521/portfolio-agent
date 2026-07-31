package com.portfolio.agent.answer.intelligence.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public final class PortfolioTaskClassification {

    private final PortfolioTaskMode mode;
    private final PortfolioConditions conditions;
    private final PortfolioRefinement refinement;
    private final double confidence;

    @JsonCreator
    public PortfolioTaskClassification(
            @JsonProperty("mode") PortfolioTaskMode mode,
            @JsonProperty("conditions") PortfolioConditions conditions,
            @JsonProperty("refinement") PortfolioRefinement refinement,
            @JsonProperty("confidence") double confidence) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.conditions = Objects.requireNonNull(conditions, "conditions");
        if (!Double.isFinite(confidence) || confidence < 0.0d || confidence > 1.0d) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        if (mode == PortfolioTaskMode.REFINE_RECOMMENDATION && refinement == null) {
            throw new IllegalArgumentException("refinement is required for REFINE_RECOMMENDATION");
        }
        if (mode != PortfolioTaskMode.REFINE_RECOMMENDATION && refinement != null) {
            throw new IllegalArgumentException("refinement is only allowed for REFINE_RECOMMENDATION");
        }
        this.refinement = refinement;
        this.confidence = confidence;
    }

    public PortfolioTaskMode getMode() {
        return mode;
    }

    public PortfolioConditions getConditions() {
        return conditions;
    }

    public PortfolioRefinement getRefinement() {
        return refinement;
    }

    public double getConfidence() {
        return confidence;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PortfolioTaskClassification classification)) {
            return false;
        }
        return Double.compare(confidence, classification.confidence) == 0
                && mode == classification.mode
                && Objects.equals(conditions, classification.conditions)
                && Objects.equals(refinement, classification.refinement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, conditions, refinement, confidence);
    }

    @Override
    public String toString() {
        return "PortfolioTaskClassification{" + "mode=" + mode
                + ", confidence=" + confidence
                + ", conditions=" + conditions
                + ", hasRefinement=" + (refinement != null) + '}';
    }
}
