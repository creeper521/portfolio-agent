package com.portfolio.agent.turn.continuation;

import com.portfolio.agent.turn.execution.AnswerSectionType;
import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.GoalRequestedOutput;
import com.portfolio.agent.turn.planning.GoalSubjectReference;
import com.portfolio.agent.turn.planning.UserGoalProposal;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Bounded text-free semantic memory for the last successfully produced turn. */
public record ConversationSemanticState(
        String contentReleaseId,
        List<GoalSummary> goals,
        Instant updatedAt) {

    public ConversationSemanticState {
        contentReleaseId = text(contentReleaseId, "contentReleaseId", 128);
        goals = List.copyOf(Objects.requireNonNull(goals, "goals"));
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (goals.isEmpty() || goals.size() > 6
                || goals.stream().map(GoalSummary::goalId).distinct().count()
                != goals.size()) {
            throw new IllegalArgumentException("semantic goals are invalid");
        }
    }

    public record GoalSummary(
            String goalId,
            GoalKind goalKind,
            List<Subject> subjects,
            Set<GoalRequestedOutput> requestedOutputs,
            Set<UserGoalProposal.Facet> facets,
            UserGoalProposal.Depth depth,
            Set<UserGoalProposal.PortfolioComparisonDimension> comparisonDimensions,
            Integer requestedSize,
            Set<String> constraints,
            List<SectionReference> sections) {

        public GoalSummary {
            goalId = id(goalId, "goalId");
            goalKind = Objects.requireNonNull(goalKind, "goalKind");
            subjects = List.copyOf(Objects.requireNonNull(subjects, "subjects"));
            requestedOutputs = Set.copyOf(Objects.requireNonNull(
                    requestedOutputs, "requestedOutputs"));
            facets = Set.copyOf(Objects.requireNonNull(facets, "facets"));
            comparisonDimensions = Set.copyOf(Objects.requireNonNull(
                    comparisonDimensions, "comparisonDimensions"));
            constraints = Set.copyOf(Objects.requireNonNull(constraints, "constraints"));
            sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
            if (subjects.size() > 5
                    || subjects.stream().distinct().count() != subjects.size()
                    || sections.size() > 12
                    || sections.stream().map(SectionReference::sectionId)
                    .distinct().count() != sections.size()
                    || requestedSize != null && (requestedSize < 1 || requestedSize > 5)
                    || constraints.stream().anyMatch(value -> value == null
                    || !value.matches("(?:CAREER_TRACK|CAPABILITY)_[A-Z0-9_]{1,64}"))) {
                throw new IllegalArgumentException("semantic goal summary is invalid");
            }
        }

        @com.fasterxml.jackson.annotation.JsonIgnore
        public boolean isPortfolioContinuationSafe() {
            return switch (goalKind) {
                case PORTFOLIO_FACT, PORTFOLIO_COMPARE, PORTFOLIO_RECOMMEND ->
                        !subjects.isEmpty();
                case GENERAL_EXPLANATION, GENERAL_COMPARISON,
                        APPLY_GENERAL_CONCEPT_TO_PORTFOLIO -> false;
            };
        }
    }

    public record Subject(GoalSubjectReference.Kind kind, String reference) {
        public Subject {
            kind = Objects.requireNonNull(kind, "kind");
            reference = text(reference, "reference", 128);
            if (kind == GoalSubjectReference.Kind.RESULT) {
                throw new IllegalArgumentException("result references are not persistable");
            }
        }
    }

    public record SectionReference(String sectionId, AnswerSectionType sectionKind) {
        public SectionReference {
            sectionId = id(sectionId, "sectionId");
            sectionKind = Objects.requireNonNull(sectionKind, "sectionKind");
            if (sectionKind == AnswerSectionType.REJECTED) {
                throw new IllegalArgumentException("rejected sections are not persistable");
            }
        }
    }

    private static String id(String value, String name) {
        if (value == null || !value.matches("[a-z0-9][a-z0-9-]{1,95}")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String text(String value, String name, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(name + " is required and bounded");
        }
        return value.trim();
    }
}
