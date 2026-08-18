package com.portfolio.agent.turn.capability.portfolio.retrieval;

import com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Raw retrieval output. Attempt metadata is deliberately not business data. */
public final class PortfolioCandidateSet {
    private final String contentReleaseId;
    private final AuthorizedSubjectScope executedScope;
    private final List<CandidateSubject> subjects;

    public PortfolioCandidateSet(
            String contentReleaseId,
            AuthorizedSubjectScope executedScope,
            List<CandidateSubject> subjects) {
        this.contentReleaseId = Objects.requireNonNull(contentReleaseId, "contentReleaseId");
        this.executedScope = Objects.requireNonNull(executedScope, "executedScope");
        this.subjects = List.copyOf(Objects.requireNonNull(subjects, "subjects"));
        if (!contentReleaseId.equals(executedScope.getContentReleaseId())) {
            throw new IllegalArgumentException("candidate release conflicts with scope");
        }
        if (subjects.size() > 64) throw new IllegalArgumentException("too many subjects");
        LinkedHashSet<String> identities = new LinkedHashSet<>();
        int units = 0;
        for (CandidateSubject subject : subjects) {
            if (!contentReleaseId.equals(subject.getContentVersion())
                    || !identities.add(subject.getSubjectId())) {
                throw new IllegalArgumentException("candidate subjects are inconsistent");
            }
            if (!authorized(subject)) {
                throw new IllegalArgumentException("candidate subject is outside authorized scope");
            }
            units += subject.getCandidates().size();
        }
        if (units > 128) throw new IllegalArgumentException("too many evidence units");
    }

    private boolean authorized(CandidateSubject subject) {
        if (executedScope.getMode() == AuthorizedSubjectScope.Mode.ALL_PUBLISHED) return true;
        return executedScope.getSubjects().stream().anyMatch(value ->
                value.getReference().equals(subject.getSubjectId())
                        && routeMatches(value.getKind(), subject.getSubjectRoute()));
    }

    private boolean routeMatches(
            com.portfolio.agent.turn.planning.GoalSubjectReference.Kind kind, String route) {
        return switch (kind) {
            case PROJECT -> route.startsWith("/projects/");
            case CASE -> route.startsWith("/cases/");
            case RESULT -> false;
        };
    }

    public String getContentReleaseId() { return contentReleaseId; }
    public AuthorizedSubjectScope getExecutedScope() { return executedScope; }
    public List<CandidateSubject> getSubjects() { return subjects; }
    public int getEvidenceUnitCount() {
        return subjects.stream().mapToInt(value -> value.getCandidates().size()).sum();
    }
}
