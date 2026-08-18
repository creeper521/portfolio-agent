package com.portfolio.agent.turn.capability.portfolio.retrieval;

import java.util.List;
import java.util.Objects;

/** Public subject metadata plus its atomic claim/evidence candidates. */
public final class CandidateSubject {

    private final String subjectId;
    private final String subjectRoute;
    private final String title;
    private final String contentVersion;
    private final List<ClaimEvidenceCandidate> candidates;

    public CandidateSubject(
            String subjectId, String subjectRoute, String title, String contentVersion,
            List<ClaimEvidenceCandidate> candidates) {
        this.subjectId = requireText(subjectId, "subjectId");
        this.subjectRoute = requireText(subjectRoute, "subjectRoute");
        this.title = requireText(title, "title");
        this.contentVersion = requireText(contentVersion, "contentVersion");
        this.candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        for (ClaimEvidenceCandidate candidate : this.candidates) {
            if (!subjectId.equals(candidate.getSubjectId())) {
                throw new IllegalArgumentException("candidate subject link is inconsistent");
            }
            if (!contentVersion.equals(candidate.getEvidence().getContentVersion())) {
                throw new IllegalArgumentException("candidate content version is inconsistent");
            }
        }
    }

    public String getSubjectId() { return subjectId; }
    public String getSubjectRoute() { return subjectRoute; }
    public String getTitle() { return title; }
    public String getContentVersion() { return contentVersion; }
    public List<ClaimEvidenceCandidate> getCandidates() { return candidates; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CandidateSubject that)) return false;
        return subjectId.equals(that.subjectId) && subjectRoute.equals(that.subjectRoute)
                && title.equals(that.title) && contentVersion.equals(that.contentVersion)
                && candidates.equals(that.candidates);
    }

    @Override
    public int hashCode() { return Objects.hash(subjectId, subjectRoute, title, contentVersion, candidates); }

    @Override
    public String toString() {
        return "CandidateSubject{subjectCount=1, candidateCount=" + candidates.size() + '}';
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}

