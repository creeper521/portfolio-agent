package com.portfolio.agent.turn.planning;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class GoalInterpretationInput {

    private final String userText;
    private final List<String> recentMessages;
    private final List<PublicSubjectDescriptor> publicSubjects;
    private final Set<GoalKind> allowedGoalKinds;
    private final InterpretationMode interpretationMode;
    private final DiscussionState discussionState;
    private final PublicSubjectDescriptor lockedSubject;
    private final List<RouteCandidate> routeCandidates;
    private final Set<SemanticRouteProposal.Route> allowedRoutes;
    private final Set<String> allowedRecommendationConstraints;

    public GoalInterpretationInput(
            String userText,
            List<String> recentMessages,
            List<PublicSubjectDescriptor> publicSubjects,
            Set<GoalKind> allowedGoalKinds) {
        this(userText, recentMessages, publicSubjects, allowedGoalKinds,
                InterpretationMode.STANDARD, DiscussionState.NONE, null,
                List.of(), Set.of(
                        SemanticRouteProposal.Route.STANDARD_GOAL,
                        SemanticRouteProposal.Route.NEEDS_CLARIFICATION), Set.of());
    }

    public GoalInterpretationInput(
            String userText,
            List<String> recentMessages,
            List<PublicSubjectDescriptor> publicSubjects,
            Set<GoalKind> allowedGoalKinds,
            InterpretationMode interpretationMode,
            DiscussionState discussionState,
            PublicSubjectDescriptor lockedSubject,
            List<RouteCandidate> routeCandidates,
            Set<SemanticRouteProposal.Route> allowedRoutes) {
        this(userText, recentMessages, publicSubjects, allowedGoalKinds,
                interpretationMode, discussionState, lockedSubject,
                routeCandidates, allowedRoutes, Set.of());
    }

    public GoalInterpretationInput(
            String userText,
            List<String> recentMessages,
            List<PublicSubjectDescriptor> publicSubjects,
            Set<GoalKind> allowedGoalKinds,
            InterpretationMode interpretationMode,
            DiscussionState discussionState,
            PublicSubjectDescriptor lockedSubject,
            List<RouteCandidate> routeCandidates,
            Set<SemanticRouteProposal.Route> allowedRoutes,
            Set<String> allowedRecommendationConstraints) {
        if (userText == null || userText.isBlank() || userText.length() > 2000) {
            throw new IllegalArgumentException("userText is required and bounded");
        }
        this.userText = userText;
        this.recentMessages = List.copyOf(
                Objects.requireNonNull(recentMessages, "recentMessages"));
        this.publicSubjects = List.copyOf(
                Objects.requireNonNull(publicSubjects, "publicSubjects"));
        this.allowedGoalKinds = Set.copyOf(
                Objects.requireNonNull(allowedGoalKinds, "allowedGoalKinds"));
        this.interpretationMode = Objects.requireNonNull(
                interpretationMode, "interpretationMode");
        this.discussionState = Objects.requireNonNull(
                discussionState, "discussionState");
        this.lockedSubject = lockedSubject;
        this.routeCandidates = List.copyOf(
                Objects.requireNonNull(routeCandidates, "routeCandidates"));
        this.allowedRoutes = Set.copyOf(
                Objects.requireNonNull(allowedRoutes, "allowedRoutes"));
        this.allowedRecommendationConstraints = Set.copyOf(Objects.requireNonNull(
                allowedRecommendationConstraints, "allowedRecommendationConstraints"));
        if (this.allowedRecommendationConstraints.stream().anyMatch(value ->
                value == null || !value.matches(
                        "(?:CAREER_TRACK|CAPABILITY)_[A-Z0-9_]{1,64}"))) {
            throw new IllegalArgumentException(
                    "allowed recommendation constraints are invalid");
        }
        if (this.allowedRoutes.isEmpty()
                || this.routeCandidates.size() > 5
                || this.routeCandidates.stream()
                .map(RouteCandidate::getCandidateKey).distinct().count()
                != this.routeCandidates.size()) {
            throw new IllegalArgumentException(
                    "semantic interpretation scope is invalid");
        }
        if (interpretationMode == InterpretationMode.STANDARD
                && discussionState != DiscussionState.NONE
                || interpretationMode == InterpretationMode.DISCUSSION
                && discussionState == DiscussionState.NONE) {
            throw new IllegalArgumentException(
                    "interpretation mode and discussion state do not match");
        }
    }

    public String getUserText() { return userText; }
    public List<String> getRecentMessages() { return recentMessages; }
    public List<PublicSubjectDescriptor> getPublicSubjects() { return publicSubjects; }
    public Set<GoalKind> getAllowedGoalKinds() { return allowedGoalKinds; }
    public InterpretationMode getInterpretationMode() { return interpretationMode; }
    public DiscussionState getDiscussionState() { return discussionState; }
    public PublicSubjectDescriptor getLockedSubject() { return lockedSubject; }
    public List<RouteCandidate> getRouteCandidates() { return routeCandidates; }
    public Set<SemanticRouteProposal.Route> getAllowedRoutes() { return allowedRoutes; }
    public Set<String> getAllowedRecommendationConstraints() {
        return allowedRecommendationConstraints;
    }
    public void requireAllowedRecommendationConstraints(Set<String> values) {
        if (!allowedRecommendationConstraints.containsAll(values)) {
            throw new IllegalArgumentException(
                    "recommendation constraints are outside the public catalog");
        }
    }

    public boolean containsPublicSubject(
            GoalSubjectReference.Kind kind, String reference) {
        return publicSubjects.stream().anyMatch(subject ->
                subject.getKind() == kind
                        && subject.getReference().equals(reference));
    }

    public static final class PublicSubjectDescriptor {
        private final GoalSubjectReference.Kind kind;
        private final String reference;
        private final String label;
        private final Set<String> reviewedAliases;

        public PublicSubjectDescriptor(
                GoalSubjectReference.Kind kind, String reference, String label) {
            this(kind, reference, label, Set.of(reference, label));
        }

        public PublicSubjectDescriptor(
                GoalSubjectReference.Kind kind,
                String reference,
                String label,
                Set<String> reviewedAliases) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.reference = requireText(reference, "reference", 128);
            this.label = requireText(label, "label", 200);
            this.reviewedAliases = Set.copyOf(
                    Objects.requireNonNull(reviewedAliases, "reviewedAliases"));
        }

        public GoalSubjectReference.Kind getKind() { return kind; }
        public String getReference() { return reference; }
        public String getLabel() { return label; }
        public Set<String> getReviewedAliases() { return reviewedAliases; }
        public boolean matchesAlias(String value) {
            return value != null
                    && reviewedAliases.stream().anyMatch(value::equals);
        }
    }

    public static final class RouteCandidate {
        private final String candidateKey;
        private final GoalSubjectReference.Kind kind;
        private final String reference;
        private final String label;
        private final Set<String> reviewedAliases;

        public RouteCandidate(
                String candidateKey,
                GoalSubjectReference.Kind kind,
                String reference,
                String label,
                Set<String> reviewedAliases) {
            if (candidateKey == null || !candidateKey.matches("C[1-5]")) {
                throw new IllegalArgumentException("candidateKey is invalid");
            }
            this.candidateKey = candidateKey;
            this.kind = Objects.requireNonNull(kind, "kind");
            this.reference = requireText(reference, "reference", 128);
            this.label = requireText(label, "label", 200);
            this.reviewedAliases = Set.copyOf(
                    Objects.requireNonNull(reviewedAliases, "reviewedAliases"));
        }

        public String getCandidateKey() { return candidateKey; }
        public GoalSubjectReference.Kind getKind() { return kind; }
        public String getReference() { return reference; }
        public String getLabel() { return label; }
        public Set<String> getReviewedAliases() { return reviewedAliases; }
    }

    public enum InterpretationMode { STANDARD, DISCUSSION }
    public enum DiscussionState { NONE, ACTIVE, EXPIRED }

    private static String requireText(
            String value, String name, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(
                    name + " is required and bounded");
        }
        return value;
    }
}
