package com.portfolio.agent.answer.routing.domain;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.CapabilityCode;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.CareerTrack;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.ComparisonDimension;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.ConstraintCode;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.ExplanationDepth;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.PortfolioFacet;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.RequestedSize;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.RoutingAudienceRole;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public interface SemanticTaskParameters {

    final class PortfolioFact implements SemanticTaskParameters {

        private final SubjectReference subject;
        private final Set<PortfolioFacet> facets;
        private final RoutingAudienceRole audienceRole;

        public PortfolioFact(SubjectReference subject, Set<String> facets, String audienceRole) {
            this.subject = Objects.requireNonNull(subject, "subject");
            this.facets = parseSet(PortfolioFacet.class, facets, "facets");
            this.audienceRole = SemanticRoutingTypes.requireEnum(
                    RoutingAudienceRole.class, audienceRole, "audienceRole");
        }

        public SubjectReference getSubject() {
            return subject;
        }

        public Set<PortfolioFacet> getFacets() {
            return facets;
        }

        public RoutingAudienceRole getAudienceRole() {
            return audienceRole;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PortfolioFact that)) {
                return false;
            }
            return Objects.equals(subject, that.subject)
                    && Objects.equals(facets, that.facets)
                    && audienceRole == that.audienceRole;
        }

        @Override
        public int hashCode() {
            return Objects.hash(subject, facets, audienceRole);
        }

        @Override
        public String toString() {
            return "PortfolioFact{facetCount=" + facets.size()
                    + ", audienceRole=" + audienceRole + '}';
        }
    }

    final class PortfolioCompare implements SemanticTaskParameters {

        private final List<SubjectReference> subjects;
        private final Set<ComparisonDimension> dimensions;
        private final RoutingAudienceRole audienceRole;

        public PortfolioCompare(List<SubjectReference> subjects, Set<String> dimensions, String audienceRole) {
            this.subjects = copySubjects(subjects, 2, 3, "subjects");
            this.dimensions = parseSet(ComparisonDimension.class, dimensions, "dimensions");
            this.audienceRole = SemanticRoutingTypes.requireEnum(
                    RoutingAudienceRole.class, audienceRole, "audienceRole");
        }

        public List<SubjectReference> getSubjects() {
            return subjects;
        }

        public Set<ComparisonDimension> getDimensions() {
            return dimensions;
        }

        public RoutingAudienceRole getAudienceRole() {
            return audienceRole;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PortfolioCompare that)) {
                return false;
            }
            return Objects.equals(subjects, that.subjects)
                    && Objects.equals(dimensions, that.dimensions)
                    && audienceRole == that.audienceRole;
        }

        @Override
        public int hashCode() {
            return Objects.hash(subjects, dimensions, audienceRole);
        }

        @Override
        public String toString() {
            return "PortfolioCompare{subjectCount=" + subjects.size()
                    + ", dimensionCount=" + dimensions.size()
                    + ", audienceRole=" + audienceRole + '}';
        }
    }

    final class PortfolioRecommend implements SemanticTaskParameters {

        private final List<SubjectReference> candidateSubjects;
        private final CareerTrack careerTrack;
        private final Set<CapabilityCode> capabilityCodes;
        private final String goal;
        private final RequestedSize requestedSize;
        private final RoutingAudienceRole audienceRole;

        public PortfolioRecommend(
                List<SubjectReference> candidateSubjects,
                String careerTrack,
                Set<String> capabilityCodes,
                String goal,
                int requestedSize,
                String audienceRole) {
            this.candidateSubjects = copySubjects(candidateSubjects, 0, Integer.MAX_VALUE, "candidateSubjects");
            this.careerTrack = SemanticRoutingTypes.requireEnum(CareerTrack.class, careerTrack, "careerTrack");
            this.capabilityCodes = parseSet(CapabilityCode.class, capabilityCodes, "capabilityCodes");
            this.goal = requireText(goal, "goal");
            this.requestedSize = RequestedSize.fromValue(requestedSize);
            this.audienceRole = SemanticRoutingTypes.requireEnum(
                    RoutingAudienceRole.class, audienceRole, "audienceRole");
        }

        public List<SubjectReference> getCandidateSubjects() {
            return candidateSubjects;
        }

        public CareerTrack getCareerTrack() {
            return careerTrack;
        }

        public Set<CapabilityCode> getCapabilityCodes() {
            return capabilityCodes;
        }

        public String getGoal() {
            return goal;
        }

        public RequestedSize getRequestedSize() {
            return requestedSize;
        }

        public RoutingAudienceRole getAudienceRole() {
            return audienceRole;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PortfolioRecommend that)) {
                return false;
            }
            return Objects.equals(candidateSubjects, that.candidateSubjects)
                    && careerTrack == that.careerTrack
                    && Objects.equals(capabilityCodes, that.capabilityCodes)
                    && Objects.equals(goal, that.goal)
                    && requestedSize == that.requestedSize
                    && audienceRole == that.audienceRole;
        }

        @Override
        public int hashCode() {
            return Objects.hash(candidateSubjects, careerTrack, capabilityCodes,
                    goal, requestedSize, audienceRole);
        }

        @Override
        public String toString() {
            return "PortfolioRecommend{candidateSubjectCount=" + candidateSubjects.size()
                    + ", careerTrack=" + careerTrack
                    + ", capabilityCount=" + capabilityCodes.size()
                    + ", requestedSize=" + requestedSize
                    + ", audienceRole=" + audienceRole + '}';
        }
    }

    final class PortfolioRefinement implements SemanticTaskParameters {

        private final SubjectReference baseResultReference;
        private final Set<ConstraintCode> addedConstraints;
        private final Set<SubjectReference> removedSubjects;

        public PortfolioRefinement(
                SubjectReference baseResultReference,
                Set<String> addedConstraints,
                Set<SubjectReference> removedSubjects) {
            this.baseResultReference = Objects.requireNonNull(baseResultReference, "baseResultReference");
            if (baseResultReference.getSubjectType() != SubjectType.RESULT) {
                throw new IllegalArgumentException("baseResultReference must be a result reference");
            }
            this.addedConstraints = parseSet(ConstraintCode.class, addedConstraints, "addedConstraints");
            this.removedSubjects = copySubjectSet(removedSubjects, "removedSubjects");
        }

        public SubjectReference getBaseResultReference() {
            return baseResultReference;
        }

        public Set<ConstraintCode> getAddedConstraints() {
            return addedConstraints;
        }

        public Set<SubjectReference> getRemovedSubjects() {
            return removedSubjects;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PortfolioRefinement that)) {
                return false;
            }
            return Objects.equals(baseResultReference, that.baseResultReference)
                    && Objects.equals(addedConstraints, that.addedConstraints)
                    && Objects.equals(removedSubjects, that.removedSubjects);
        }

        @Override
        public int hashCode() {
            return Objects.hash(baseResultReference, addedConstraints, removedSubjects);
        }

        @Override
        public String toString() {
            return "PortfolioRefinement{addedConstraintCount=" + addedConstraints.size()
                    + ", removedSubjectCount=" + removedSubjects.size() + '}';
        }
    }

    final class GeneralExplanation implements SemanticTaskParameters {

        private final String topic;
        private final ExplanationDepth depth;
        private final RoutingAudienceRole audienceRole;

        public GeneralExplanation(String topic, String depth, String audienceRole) {
            this.topic = requireText(topic, "topic");
            this.depth = SemanticRoutingTypes.requireEnum(ExplanationDepth.class, depth, "depth");
            this.audienceRole = SemanticRoutingTypes.requireEnum(
                    RoutingAudienceRole.class, audienceRole, "audienceRole");
        }

        public String getTopic() {
            return topic;
        }

        public ExplanationDepth getDepth() {
            return depth;
        }

        public RoutingAudienceRole getAudienceRole() {
            return audienceRole;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof GeneralExplanation that)) {
                return false;
            }
            return Objects.equals(topic, that.topic)
                    && depth == that.depth
                    && audienceRole == that.audienceRole;
        }

        @Override
        public int hashCode() {
            return Objects.hash(topic, depth, audienceRole);
        }

        @Override
        public String toString() {
            return "GeneralExplanation{depth=" + depth + ", audienceRole=" + audienceRole + '}';
        }
    }

    final class GeneralComparison implements SemanticTaskParameters {

        private final List<String> subjects;
        private final Set<ComparisonDimension> dimensions;
        private final ExplanationDepth depth;
        private final RoutingAudienceRole audienceRole;

        public GeneralComparison(
                List<String> subjects,
                Set<String> dimensions,
                String depth,
                String audienceRole) {
            this.subjects = copyTextList(subjects, 2, 3, "subjects");
            this.dimensions = parseSet(ComparisonDimension.class, dimensions, "dimensions");
            this.depth = SemanticRoutingTypes.requireEnum(ExplanationDepth.class, depth, "depth");
            this.audienceRole = SemanticRoutingTypes.requireEnum(
                    RoutingAudienceRole.class, audienceRole, "audienceRole");
        }

        public List<String> getSubjects() {
            return subjects;
        }

        public Set<ComparisonDimension> getDimensions() {
            return dimensions;
        }

        public ExplanationDepth getDepth() {
            return depth;
        }

        public RoutingAudienceRole getAudienceRole() {
            return audienceRole;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof GeneralComparison that)) {
                return false;
            }
            return Objects.equals(subjects, that.subjects)
                    && Objects.equals(dimensions, that.dimensions)
                    && depth == that.depth
                    && audienceRole == that.audienceRole;
        }

        @Override
        public int hashCode() {
            return Objects.hash(subjects, dimensions, depth, audienceRole);
        }

        @Override
        public String toString() {
            return "GeneralComparison{subjectCount=" + subjects.size()
                    + ", dimensionCount=" + dimensions.size()
                    + ", depth=" + depth
                    + ", audienceRole=" + audienceRole + '}';
        }
    }

    final class Synthesis implements SemanticTaskParameters {

        private final List<String> sourceTaskIds;
        private final String synthesisGoal;
        private final Set<ComparisonDimension> dimensions;

        public Synthesis(List<String> sourceTaskIds, String synthesisGoal, Set<String> dimensions) {
            this.sourceTaskIds = copyTextList(sourceTaskIds, 2, Integer.MAX_VALUE, "sourceTaskIds");
            if (new LinkedHashSet<>(this.sourceTaskIds).size() != this.sourceTaskIds.size()) {
                throw new IllegalArgumentException("sourceTaskIds must be distinct");
            }
            this.synthesisGoal = requireText(synthesisGoal, "synthesisGoal");
            this.dimensions = parseSet(ComparisonDimension.class, dimensions, "dimensions");
        }

        public List<String> getSourceTaskIds() {
            return sourceTaskIds;
        }

        public String getSynthesisGoal() {
            return synthesisGoal;
        }

        public Set<ComparisonDimension> getDimensions() {
            return dimensions;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Synthesis that)) {
                return false;
            }
            return Objects.equals(sourceTaskIds, that.sourceTaskIds)
                    && Objects.equals(synthesisGoal, that.synthesisGoal)
                    && Objects.equals(dimensions, that.dimensions);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceTaskIds, synthesisGoal, dimensions);
        }

        @Override
        public String toString() {
            return "Synthesis{sourceTaskCount=" + sourceTaskIds.size()
                    + ", dimensionCount=" + dimensions.size() + '}';
        }
    }

    private static <E extends Enum<E>> Set<E> parseSet(
            Class<E> type, Set<String> values, String name) {
        Objects.requireNonNull(values, name);
        Set<E> parsed = new LinkedHashSet<>();
        for (String value : values) {
            parsed.add(SemanticRoutingTypes.requireEnum(type, value, name));
        }
        return Set.copyOf(parsed);
    }

    private static List<SubjectReference> copySubjects(
            List<SubjectReference> values, int minimum, int maximum, String name) {
        Objects.requireNonNull(values, name);
        if (values.size() < minimum || values.size() > maximum) {
            throw new IllegalArgumentException(name + " must contain between " + minimum + " and " + maximum + " values");
        }
        List<SubjectReference> copied = List.copyOf(values);
        if (new LinkedHashSet<>(copied).size() != copied.size()) {
            throw new IllegalArgumentException(name + " must not contain duplicates");
        }
        return copied;
    }

    private static Set<SubjectReference> copySubjectSet(Set<SubjectReference> values, String name) {
        Objects.requireNonNull(values, name);
        return Set.copyOf(values);
    }

    private static List<String> copyTextList(
            List<String> values, int minimum, int maximum, String name) {
        Objects.requireNonNull(values, name);
        if (values.size() < minimum || values.size() > maximum) {
            throw new IllegalArgumentException(name + " must contain between " + minimum + " and " + maximum + " values");
        }
        List<String> copied = new ArrayList<>();
        Set<String> normalizedValues = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = requireText(value, name);
            if (!normalizedValues.add(normalized.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(name + " must not contain duplicate values");
            }
            copied.add(normalized);
        }
        return List.copyOf(copied);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
