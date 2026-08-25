package com.portfolio.agent.turn.capability.portfolio.retrieval;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Public subject metadata plus its atomic claim/evidence candidates.
 *
 * <p>检索候选主体（不可变值对象）：一个公开主体的元数据与其原子 claim/Evidence 候选。
 * 不变量：每个候选的 subjectId 必须等于本主体标识、候选 Evidence 的 contentVersion
 * 必须与本主体一致（禁止跨版本混合），违反即抛出 IllegalArgumentException。
 * toString 刻意只输出计数，不泄露主体内容。
 */
public final class CandidateSubject {

    private final String subjectId;
    private final String subjectRoute;
    private final String title;
    private final String contentVersion;
    private final String careerTrack;
    private final Set<String> capabilityCodes;
    private final List<ClaimEvidenceCandidate> candidates;

    public CandidateSubject(
            String subjectId, String subjectRoute, String title, String contentVersion,
            List<ClaimEvidenceCandidate> candidates) {
        this(subjectId, subjectRoute, title, contentVersion, null, Set.of(), candidates);
    }

    public CandidateSubject(
            String subjectId, String subjectRoute, String title, String contentVersion,
            String careerTrack, Set<String> capabilityCodes,
            List<ClaimEvidenceCandidate> candidates) {
        this.subjectId = requireText(subjectId, "subjectId");
        this.subjectRoute = requireText(subjectRoute, "subjectRoute");
        this.title = requireText(title, "title");
        this.contentVersion = requireText(contentVersion, "contentVersion");
        this.careerTrack = careerTrack == null || careerTrack.isBlank()
                ? null : careerTrack.trim();
        this.capabilityCodes = Set.copyOf(Objects.requireNonNull(
                capabilityCodes, "capabilityCodes"));
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
    public String getCareerTrack() { return careerTrack; }
    public Set<String> getCapabilityCodes() { return capabilityCodes; }
    public List<ClaimEvidenceCandidate> getCandidates() { return candidates; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CandidateSubject that)) return false;
        return subjectId.equals(that.subjectId) && subjectRoute.equals(that.subjectRoute)
                && title.equals(that.title) && contentVersion.equals(that.contentVersion)
                && Objects.equals(careerTrack, that.careerTrack)
                && capabilityCodes.equals(that.capabilityCodes)
                && candidates.equals(that.candidates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectId, subjectRoute, title, contentVersion,
                careerTrack, capabilityCodes, candidates);
    }

    @Override
    public String toString() {
        return "CandidateSubject{subjectCount=1, candidateCount=" + candidates.size() + '}';
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
