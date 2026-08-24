package com.portfolio.agent.turn.planning;

import com.portfolio.agent.turn.continuation.ConversationSemanticState;

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
    private final PublicSubjectDescriptor defaultSubject;
    private final SemanticTaskParameters.AudienceProfile audienceProfile;
    private final ConversationSemanticState recentSemanticState;

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
        this(userText, recentMessages, publicSubjects, allowedGoalKinds,
                interpretationMode, discussionState, lockedSubject,
                routeCandidates, allowedRoutes, allowedRecommendationConstraints,
                null, SemanticTaskParameters.AudienceProfile.GUEST);
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
            Set<String> allowedRecommendationConstraints,
            PublicSubjectDescriptor defaultSubject,
            SemanticTaskParameters.AudienceProfile audienceProfile) {
        this(userText, recentMessages, publicSubjects, allowedGoalKinds,
                interpretationMode, discussionState, lockedSubject,
                routeCandidates, allowedRoutes, allowedRecommendationConstraints,
                defaultSubject, audienceProfile, null);
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
            Set<String> allowedRecommendationConstraints,
            PublicSubjectDescriptor defaultSubject,
            SemanticTaskParameters.AudienceProfile audienceProfile,
            ConversationSemanticState recentSemanticState) {
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
        this.defaultSubject = defaultSubject;
        this.audienceProfile = Objects.requireNonNull(audienceProfile, "audienceProfile");
        this.recentSemanticState = recentSemanticState;
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
        if (defaultSubject != null && (interpretationMode != InterpretationMode.STANDARD
                || publicSubjects.stream().noneMatch(subject ->
                subject.getKind() == defaultSubject.getKind()
                        && subject.getReference().equals(defaultSubject.getReference())))) {
            throw new IllegalArgumentException("default subject is outside public scope");
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
    public PublicSubjectDescriptor getDefaultSubject() { return defaultSubject; }
    public SemanticTaskParameters.AudienceProfile getAudienceProfile() {
        return audienceProfile;
    }
    public ConversationSemanticState getRecentSemanticState() {
        return recentSemanticState;
    }
    public PublicSubjectDescriptor recentPortfolioSubject() {
        if (recentSemanticState == null) return null;
        List<ConversationSemanticState.GoalSummary> safeGoals = recentSemanticState.goals().stream()
                .filter(ConversationSemanticState.GoalSummary::isPortfolioContinuationSafe)
                .toList();
        if (safeGoals.size() != 1) return null;
        return recentPortfolioSubject(safeGoals.getFirst().goalId(), null);
    }
    public PublicSubjectDescriptor recentPortfolioSubject(
            String goalId, String sectionId) {
        if (recentSemanticState == null) return null;
        ConversationSemanticState.GoalSummary goal = recentSemanticState.goals().stream()
                .filter(candidate -> candidate.goalId().equals(goalId)
                        && candidate.isPortfolioContinuationSafe())
                .findFirst().orElse(null);
        if (goal == null || goal.subjects().size() != 1
                || sectionId != null && goal.sections().stream().noneMatch(
                section -> section.sectionId().equals(sectionId))) {
            return null;
        }
        ConversationSemanticState.Subject recent = goal.subjects().getFirst();
        return publicSubjects.stream().filter(subject ->
                subject.getKind() == recent.kind()
                        && subject.getReference().equals(recent.reference()))
                .findFirst().orElse(null);
    }
    public UserGoalProposal.Facet recentSectionFacet(
            String goalId, String sectionId) {
        if (recentSemanticState == null || sectionId == null) return null;
        return recentSemanticState.goals().stream()
                .filter(goal -> goal.goalId().equals(goalId)
                        && goal.isPortfolioContinuationSafe())
                .flatMap(goal -> goal.sections().stream())
                .filter(section -> section.sectionId().equals(sectionId))
                .findFirst()
                .map(section -> switch (section.sectionKind()) {
                    case BACKGROUND -> UserGoalProposal.Facet.BACKGROUND;
                    case RESPONSIBILITY -> UserGoalProposal.Facet.RESPONSIBILITY;
                    case SOLUTION -> UserGoalProposal.Facet.SOLUTION;
                    case VERIFICATION -> UserGoalProposal.Facet.VERIFICATION;
                    case STATUS -> UserGoalProposal.Facet.STATUS;
                    case BOUNDARY, GENERAL_PRINCIPLE, PORTFOLIO_EXAMPLE,
                            RELATION, REJECTED -> null;
                })
                .orElse(null);
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
