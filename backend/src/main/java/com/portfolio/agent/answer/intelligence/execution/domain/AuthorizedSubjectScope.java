package com.portfolio.agent.answer.intelligence.execution.domain;

import com.portfolio.agent.answer.routing.domain.SubjectReference;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Closed subject authorization compiled by P2/P3, never inferred by retrieval. */
public final class AuthorizedSubjectScope {

    public enum ScopeMode {
        EXACT_SUBJECTS,
        ALL_PUBLISHED_CANDIDATES
    }

    private final ScopeMode mode;
    private final List<SubjectReference> exactSubjects;
    private final String contentVersion;

    private AuthorizedSubjectScope(
            ScopeMode mode, List<SubjectReference> exactSubjects, String contentVersion) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.exactSubjects = List.copyOf(Objects.requireNonNull(exactSubjects, "exactSubjects"));
        this.contentVersion = requireText(contentVersion, "contentVersion");
        if (mode == ScopeMode.EXACT_SUBJECTS && this.exactSubjects.isEmpty()) {
            throw new IllegalArgumentException("exact scope requires subjects");
        }
        if (mode == ScopeMode.ALL_PUBLISHED_CANDIDATES && !this.exactSubjects.isEmpty()) {
            throw new IllegalArgumentException("all-published scope must not carry exact subjects");
        }
        if (new LinkedHashSet<>(this.exactSubjects).size() != this.exactSubjects.size()) {
            throw new IllegalArgumentException("exact subjects must be distinct");
        }
    }

    public static AuthorizedSubjectScope exactSubjects(
            List<SubjectReference> subjects, String contentVersion) {
        return new AuthorizedSubjectScope(ScopeMode.EXACT_SUBJECTS, subjects, contentVersion);
    }

    public static AuthorizedSubjectScope allPublishedCandidates(String contentVersion) {
        return new AuthorizedSubjectScope(ScopeMode.ALL_PUBLISHED_CANDIDATES, List.of(), contentVersion);
    }

    public ScopeMode getMode() {
        return mode;
    }

    public List<SubjectReference> getExactSubjects() {
        return exactSubjects;
    }

    public String getContentVersion() {
        return contentVersion;
    }

    public boolean contains(SubjectReference subject) {
        Objects.requireNonNull(subject, "subject");
        if (mode == ScopeMode.ALL_PUBLISHED_CANDIDATES) {
            return true;
        }
        return exactSubjects.stream().anyMatch(authorized ->
                authorized.getSubjectType() == subject.getSubjectType()
                        && authorized.getSubjectId().equals(subject.getSubjectId())
                        && authorized.getContentVersion().equals(subject.getContentVersion()));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AuthorizedSubjectScope that)) {
            return false;
        }
        return mode == that.mode && exactSubjects.equals(that.exactSubjects)
                && contentVersion.equals(that.contentVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, exactSubjects, contentVersion);
    }

    @Override
    public String toString() {
        return "AuthorizedSubjectScope{mode=" + mode + ", subjectCount="
                + exactSubjects.size() + '}';
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
