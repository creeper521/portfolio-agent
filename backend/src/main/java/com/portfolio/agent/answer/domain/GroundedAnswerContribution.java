package com.portfolio.agent.answer.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Evidence-independent material that P2 synthesis may consume after P3 has
 * completed validation. It deliberately carries no question, prompt, or raw
 * retrieval object.
 */
public final class GroundedAnswerContribution {

    private final List<String> supportedStatements;
    private final List<String> publicSourceReferences;
    private final List<PublicSourceReferenceValue> sourceReferences;
    private final List<String> caveats;
    private final List<String> omittedTopicLabels;

    public GroundedAnswerContribution(
            List<String> supportedStatements,
            List<String> publicSourceReferences,
            List<String> caveats,
            List<String> omittedTopicLabels) {
        this(supportedStatements, publicSourceReferences, List.of(), caveats, omittedTopicLabels);
    }

    public GroundedAnswerContribution(
            List<String> supportedStatements,
            List<String> publicSourceReferences,
            List<PublicSourceReferenceValue> sourceReferences,
            List<String> caveats,
            List<String> omittedTopicLabels) {
        this.supportedStatements = copyDistinct(supportedStatements, "supportedStatements");
        this.publicSourceReferences = copyDistinct(publicSourceReferences, "publicSourceReferences");
        this.sourceReferences = List.copyOf(Objects.requireNonNull(sourceReferences, "sourceReferences"));
        this.caveats = copyDistinct(caveats, "caveats");
        this.omittedTopicLabels = copyDistinct(omittedTopicLabels, "omittedTopicLabels");
        if (this.supportedStatements.isEmpty() && this.omittedTopicLabels.isEmpty()) {
            throw new IllegalArgumentException("a contribution must contain statements or omitted topics");
        }
        if (!this.supportedStatements.isEmpty() && this.publicSourceReferences.isEmpty()) {
            throw new IllegalArgumentException("supported statements require public sources");
        }
    }

    public List<String> getSupportedStatements() {
        return supportedStatements;
    }

    public List<String> getPublicSourceReferences() {
        return publicSourceReferences;
    }

    public List<PublicSourceReferenceValue> getSourceReferences() {
        return sourceReferences;
    }

    public List<String> getCaveats() {
        return caveats;
    }

    public List<String> getOmittedTopicLabels() {
        return omittedTopicLabels;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GroundedAnswerContribution that)) {
            return false;
        }
        return supportedStatements.equals(that.supportedStatements)
                && publicSourceReferences.equals(that.publicSourceReferences)
                && sourceReferences.equals(that.sourceReferences)
                && caveats.equals(that.caveats)
                && omittedTopicLabels.equals(that.omittedTopicLabels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(supportedStatements, publicSourceReferences, caveats, omittedTopicLabels);
    }

    @Override
    public String toString() {
        return "GroundedAnswerContribution{statementCount=" + supportedStatements.size()
                + ", sourceReferenceCount=" + publicSourceReferences.size()
                + ", caveatCount=" + caveats.size()
                + ", omittedTopicCount=" + omittedTopicLabels.size() + '}';
    }

    private static List<String> copyDistinct(List<String> values, String name) {
        Objects.requireNonNull(values, name);
        List<String> copied = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " must not contain blank values");
            }
            String normalized = value.trim();
            if (!seen.add(normalized)) {
                throw new IllegalArgumentException(name + " must not contain duplicates");
            }
            copied.add(normalized);
        }
        return List.copyOf(copied);
    }
}
