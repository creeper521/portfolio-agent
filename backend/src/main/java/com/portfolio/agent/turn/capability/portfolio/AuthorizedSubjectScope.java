package com.portfolio.agent.turn.capability.portfolio;

import com.portfolio.agent.turn.planning.GoalSubjectReference;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public final class AuthorizedSubjectScope {
    private final Mode mode;
    private final List<Subject> subjects;
    private final String contentReleaseId;

    private AuthorizedSubjectScope(Mode mode, List<Subject> subjects, String contentReleaseId) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.subjects = List.copyOf(Objects.requireNonNull(subjects, "subjects"));
        if (contentReleaseId == null || contentReleaseId.isBlank()) {
            throw new IllegalArgumentException("contentReleaseId is required");
        }
        this.contentReleaseId = contentReleaseId;
        if (mode == Mode.EXACT && this.subjects.isEmpty()) {
            throw new IllegalArgumentException("exact scope requires subjects");
        }
        if (mode == Mode.ALL_PUBLISHED && !this.subjects.isEmpty()) {
            throw new IllegalArgumentException("all-published scope cannot carry subjects");
        }
        if (new LinkedHashSet<>(this.subjects).size() != this.subjects.size()) {
            throw new IllegalArgumentException("subjects must be distinct");
        }
    }

    public static AuthorizedSubjectScope exact(
            List<GoalSubjectReference> references, String contentReleaseId) {
        return new AuthorizedSubjectScope(Mode.EXACT, references.stream()
                .map(value -> new Subject(value.getKind(), value.getReference())).toList(),
                contentReleaseId);
    }

    public static AuthorizedSubjectScope allPublished(String contentReleaseId) {
        return new AuthorizedSubjectScope(Mode.ALL_PUBLISHED, List.of(), contentReleaseId);
    }

    public Mode getMode() { return mode; }
    public List<Subject> getSubjects() { return subjects; }
    public String getContentReleaseId() { return contentReleaseId; }

    public enum Mode { EXACT, ALL_PUBLISHED }

    public static final class Subject {
        private final GoalSubjectReference.Kind kind;
        private final String reference;
        public Subject(GoalSubjectReference.Kind kind, String reference) {
            this.kind = Objects.requireNonNull(kind, "kind");
            if (reference == null || reference.isBlank()) {
                throw new IllegalArgumentException("reference is required");
            }
            this.reference = reference;
        }
        public GoalSubjectReference.Kind getKind() { return kind; }
        public String getReference() { return reference; }
        @Override public boolean equals(Object other) {
            return other instanceof Subject that && kind == that.kind && reference.equals(that.reference);
        }
        @Override public int hashCode() { return Objects.hash(kind, reference); }
    }
}
