package com.portfolio.agent.answer.routing.domain;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectResolutionSource;

import java.util.Objects;

/** Internal routing binding; the public SubjectReference shape remains unchanged. */
public final class ResolvedSubjectBinding {

    private final SubjectReference subject;
    private final SubjectBindingRole role;
    private final SubjectResolutionSource source;

    public ResolvedSubjectBinding(
            SubjectReference subject,
            SubjectBindingRole role,
            SubjectResolutionSource source) {
        this.subject = Objects.requireNonNull(subject, "subject");
        this.role = Objects.requireNonNull(role, "role");
        this.source = Objects.requireNonNull(source, "source");
    }

    public SubjectReference getSubject() {
        return subject;
    }

    public SubjectBindingRole getRole() {
        return role;
    }

    public SubjectResolutionSource getSource() {
        return source;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ResolvedSubjectBinding that)) {
            return false;
        }
        return Objects.equals(subject, that.subject)
                && role == that.role
                && source == that.source;
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject, role, source);
    }

    @Override
    public String toString() {
        return "ResolvedSubjectBinding{role=" + role + ", source=" + source
                + ", subjectId=<redacted>}";
    }
}
