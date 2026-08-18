package com.portfolio.agent.turn.planning;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class GoalInterpretationInput {

    private final String userText;
    private final List<String> recentMessages;
    private final List<PublicSubjectDescriptor> publicSubjects;
    private final Set<GoalKind> allowedGoalKinds;

    public GoalInterpretationInput(
            String userText,
            List<String> recentMessages,
            List<PublicSubjectDescriptor> publicSubjects,
            Set<GoalKind> allowedGoalKinds) {
        if (userText == null || userText.isBlank() || userText.length() > 2000) {
            throw new IllegalArgumentException("userText is required and bounded");
        }
        this.userText = userText;
        this.recentMessages = List.copyOf(Objects.requireNonNull(recentMessages, "recentMessages"));
        this.publicSubjects = List.copyOf(Objects.requireNonNull(publicSubjects, "publicSubjects"));
        this.allowedGoalKinds = Set.copyOf(Objects.requireNonNull(allowedGoalKinds, "allowedGoalKinds"));
    }

    public String getUserText() { return userText; }
    public List<String> getRecentMessages() { return recentMessages; }
    public List<PublicSubjectDescriptor> getPublicSubjects() { return publicSubjects; }
    public Set<GoalKind> getAllowedGoalKinds() { return allowedGoalKinds; }

    public boolean containsPublicSubject(GoalSubjectReference.Kind kind, String reference) {
        return publicSubjects.stream().anyMatch(subject -> subject.getKind() == kind
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
            return value != null && reviewedAliases.stream().anyMatch(value::equals);
        }
    }

    private static String requireText(String value, String name, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(name + " is required and bounded");
        }
        return value;
    }
}
