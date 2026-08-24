package com.portfolio.agent.turn.planning;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class UserGoalProposal {

    private final List<ProposedGoal> goals;

    public UserGoalProposal(List<ProposedGoal> goals) {
        List<ProposedGoal> copied = List.copyOf(Objects.requireNonNull(goals, "goals"));
        if (copied.isEmpty() || copied.size() > 6) {
            throw new IllegalArgumentException("goals must contain between one and six items");
        }
        if (copied.stream().map(ProposedGoal::getGoalKey).distinct().count() != copied.size()) {
            throw new IllegalArgumentException("goal keys must be unique");
        }
        this.goals = copied;
    }

    public List<ProposedGoal> getGoals() {
        return goals;
    }

    public static final class ProposedGoal {
        private final String goalKey;
        private final GoalKind goalKind;
        private final InputAnchor inputAnchor;
        private final List<GoalSubjectReference> subjectCandidates;
        private final GoalKnowledgeRequirement knowledgeRequirement;
        private final GoalParameters parameters;

        public ProposedGoal(
                String goalKey,
                GoalKind goalKind,
                InputAnchor inputAnchor,
                List<GoalSubjectReference> subjectCandidates,
                Set<GoalRequestedOutput> requestedOutputs,
                GoalKnowledgeRequirement knowledgeRequirement,
                GoalParameters parameters) {
            if (goalKey == null || !goalKey.matches("[a-z0-9][a-z0-9-]{1,63}")) {
                throw new IllegalArgumentException("goalKey must be a local key");
            }
            this.goalKey = goalKey;
            this.goalKind = Objects.requireNonNull(goalKind, "goalKind");
            this.inputAnchor = Objects.requireNonNull(inputAnchor, "inputAnchor");
            this.subjectCandidates = List.copyOf(
                    Objects.requireNonNull(subjectCandidates, "subjectCandidates"));
            Set<GoalRequestedOutput> suppliedOutputs = Set.copyOf(
                    Objects.requireNonNull(requestedOutputs, "requestedOutputs"));
            this.knowledgeRequirement = Objects.requireNonNull(
                    knowledgeRequirement, "knowledgeRequirement");
            this.parameters = Objects.requireNonNull(parameters, "parameters");
            if (parameters.getGoalKind() != goalKind) {
                throw new IllegalArgumentException("goal parameters must match goalKind");
            }
            if (!suppliedOutputs.equals(requestedOutputs(parameters))) {
                throw new IllegalArgumentException(
                        "requestedOutputs must match the typed goal parameters");
            }
        }

        public String getGoalKey() { return goalKey; }
        public GoalKind getGoalKind() { return goalKind; }
        public InputAnchor getInputAnchor() { return inputAnchor; }
        public List<GoalSubjectReference> getSubjectCandidates() { return subjectCandidates; }
        public Set<GoalRequestedOutput> getRequestedOutputs() {
            return requestedOutputs(parameters);
        }
        public GoalKnowledgeRequirement getKnowledgeRequirement() { return knowledgeRequirement; }
        public GoalParameters getParameters() { return parameters; }
    }

    public interface GoalParameters {
        GoalKind getGoalKind();
    }

    public static final class PortfolioFactParameters implements GoalParameters {
        private final Set<Facet> facets;
        private final Depth depth;

        public PortfolioFactParameters(Set<Facet> facets, Depth depth) {
            this.facets = Set.copyOf(Objects.requireNonNull(facets, "facets"));
            if (this.facets.isEmpty()) {
                throw new IllegalArgumentException("portfolio fact facets must not be empty");
            }
            this.depth = Objects.requireNonNull(depth, "depth");
        }

        @Override public GoalKind getGoalKind() { return GoalKind.PORTFOLIO_FACT; }
        public Set<Facet> getFacets() { return facets; }
        public Depth getDepth() { return depth; }
    }

    public static final class PortfolioCompareParameters implements GoalParameters {
        private final Set<PortfolioComparisonDimension> dimensions;

        public PortfolioCompareParameters(Set<PortfolioComparisonDimension> dimensions) {
            this.dimensions = Set.copyOf(Objects.requireNonNull(dimensions, "dimensions"));
            if (this.dimensions.isEmpty()) {
                throw new IllegalArgumentException("dimensions must not be empty");
            }
        }

        @Override public GoalKind getGoalKind() { return GoalKind.PORTFOLIO_COMPARE; }
        public Set<PortfolioComparisonDimension> getDimensions() { return dimensions; }
    }

    public static final class PortfolioRecommendationParameters implements GoalParameters {
        private final int requestedSize;
        private final Set<String> constraints;

        public PortfolioRecommendationParameters(int requestedSize, Set<String> constraints) {
            if (requestedSize < 1 || requestedSize > 5) {
                throw new IllegalArgumentException("requestedSize must be between one and five");
            }
            this.requestedSize = requestedSize;
            this.constraints = copyNamesAllowEmpty(constraints, "constraints");
        }

        @Override public GoalKind getGoalKind() { return GoalKind.PORTFOLIO_RECOMMEND; }
        public int getRequestedSize() { return requestedSize; }
        public Set<String> getConstraints() { return constraints; }
    }

    public static final class GeneralExplanationParameters implements GoalParameters {
        private final InputAnchor topicAnchor;
        private final Depth depth;

        public GeneralExplanationParameters(InputAnchor topicAnchor, Depth depth) {
            this.topicAnchor = Objects.requireNonNull(topicAnchor, "topicAnchor");
            this.depth = Objects.requireNonNull(depth, "depth");
        }

        @Override public GoalKind getGoalKind() { return GoalKind.GENERAL_EXPLANATION; }
        public InputAnchor getTopicAnchor() { return topicAnchor; }
        public Depth getDepth() { return depth; }
    }

    public static final class GeneralComparisonParameters implements GoalParameters {
        private final List<InputAnchor> subjectAnchors;
        private final Set<String> dimensions;

        public GeneralComparisonParameters(List<InputAnchor> subjectAnchors, Set<String> dimensions) {
            this.subjectAnchors = List.copyOf(Objects.requireNonNull(subjectAnchors, "subjectAnchors"));
            if (this.subjectAnchors.size() < 2 || this.subjectAnchors.size() > 5) {
                throw new IllegalArgumentException("general comparison requires two to five subjects");
            }
            this.dimensions = copyNames(dimensions, "dimensions");
        }

        @Override public GoalKind getGoalKind() { return GoalKind.GENERAL_COMPARISON; }
        public List<InputAnchor> getSubjectAnchors() { return subjectAnchors; }
        public Set<String> getDimensions() { return dimensions; }
    }

    public static final class ApplyConceptParameters implements GoalParameters {
        private final InputAnchor conceptAnchor;
        private final Facet portfolioFacet;
        private final Depth depth;

        public ApplyConceptParameters(
                InputAnchor conceptAnchor, Facet portfolioFacet, Depth depth) {
            this.conceptAnchor = Objects.requireNonNull(conceptAnchor, "conceptAnchor");
            this.portfolioFacet = Objects.requireNonNull(portfolioFacet, "portfolioFacet");
            this.depth = Objects.requireNonNull(depth, "depth");
        }

        @Override public GoalKind getGoalKind() { return GoalKind.APPLY_GENERAL_CONCEPT_TO_PORTFOLIO; }
        public InputAnchor getConceptAnchor() { return conceptAnchor; }
        public Facet getPortfolioFacet() { return portfolioFacet; }
        public Depth getDepth() { return depth; }
    }

    public static final class InputAnchor {
        private final String text;
        private final int start;

        public InputAnchor(String text, int start) {
            if (text == null || text.isBlank() || text.length() > 256 || start < 0) {
                throw new IllegalArgumentException("input anchor is invalid");
            }
            this.text = text;
            this.start = start;
        }

        public String getText() { return text; }
        public int getStart() { return start; }

        public void requireMatches(String input) {
            if (start + text.length() > input.length()
                    || !input.regionMatches(start, text, 0, text.length())) {
                throw new IllegalArgumentException("anchor does not match original input");
            }
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof InputAnchor that)) return false;
            return start == that.start && text.equals(that.text);
        }

        @Override
        public int hashCode() { return Objects.hash(text, start); }
    }

    public enum Facet { OVERVIEW, BACKGROUND, RESPONSIBILITY, SOLUTION, VERIFICATION, STATUS }
    public enum Depth { CONCISE, STANDARD, DETAILED }
    public enum PortfolioComparisonDimension {
        ARCHITECTURE, IMPLEMENTATION, OUTCOME, RISKS, VERIFICATION
    }

    private static Set<GoalRequestedOutput> requestedOutputs(GoalParameters parameters) {
        if (parameters instanceof PortfolioFactParameters fact) {
            return fact.getFacets().stream()
                    .map(value -> GoalRequestedOutput.valueOf(value.name()))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        return switch (parameters.getGoalKind()) {
            case PORTFOLIO_COMPARE, GENERAL_COMPARISON ->
                    Set.of(GoalRequestedOutput.COMPARISON);
            case PORTFOLIO_RECOMMEND -> Set.of(GoalRequestedOutput.RECOMMENDATION);
            case GENERAL_EXPLANATION -> Set.of(GoalRequestedOutput.EXPLANATION);
            case APPLY_GENERAL_CONCEPT_TO_PORTFOLIO -> Set.of(GoalRequestedOutput.RELATION);
            case PORTFOLIO_FACT -> throw new IllegalArgumentException(
                    "portfolio fact parameters are required");
        };
    }

    private static Set<String> copyNames(Set<String> values, String name) {
        Set<String> copied = copyNamesAllowEmpty(values, name);
        if (copied.isEmpty()) throw new IllegalArgumentException(name + " must not be empty");
        return copied;
    }

    private static Set<String> copyNamesAllowEmpty(Set<String> values, String name) {
        Set<String> copied = Set.copyOf(Objects.requireNonNull(values, name));
        for (String value : copied) {
            if (value == null || !value.matches("[A-Z_]{1,64}")) {
                throw new IllegalArgumentException(name + " must contain closed names");
            }
        }
        return copied;
    }
}
