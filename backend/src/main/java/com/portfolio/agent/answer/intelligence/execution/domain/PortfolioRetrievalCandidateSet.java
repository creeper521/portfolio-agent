package com.portfolio.agent.answer.intelligence.execution.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Formal material boundary between a retrieval adapter and P3 validation. */
public final class PortfolioRetrievalCandidateSet {

    public static final int MAX_SUBJECTS = 64;
    public static final int MAX_UNITS = 128;
    public static final int MAX_UNITS_PER_SUBJECT = 16;

    private final String capabilityId;
    private final int attempt;
    private final String returnedContentVersion;
    private final AuthorizedSubjectScope executedScope;
    private final List<CandidateSubject> candidateSubjects;
    private final CandidateCoverageReport coverageReport;

    public PortfolioRetrievalCandidateSet(
            String capabilityId, int attempt, String returnedContentVersion,
            AuthorizedSubjectScope executedScope, List<CandidateSubject> candidateSubjects,
            CandidateCoverageReport coverageReport) {
        this.capabilityId = requireText(capabilityId, "capabilityId");
        if (!PortfolioCapabilityId.isSupported(capabilityId)) {
            throw new IllegalArgumentException("candidate set capability is unsupported");
        }
        if (attempt < 1 || attempt > 2) throw new IllegalArgumentException("attempt must be 1 or 2");
        this.attempt = attempt;
        this.returnedContentVersion = requireText(returnedContentVersion, "returnedContentVersion");
        this.executedScope = Objects.requireNonNull(executedScope, "executedScope");
        this.candidateSubjects = List.copyOf(Objects.requireNonNull(candidateSubjects, "candidateSubjects"));
        this.coverageReport = Objects.requireNonNull(coverageReport, "coverageReport");
        if (!returnedContentVersion.equals(executedScope.getContentVersion())) {
            throw new IllegalArgumentException("candidate set content version conflicts with scope");
        }
        if (this.candidateSubjects.size() > MAX_SUBJECTS) {
            throw new IllegalArgumentException("LIMIT_EXCEEDED_SUBJECTS");
        }
        LinkedHashSet<String> subjectIds = new LinkedHashSet<>();
        int unitCount = 0;
        for (CandidateSubject subject : this.candidateSubjects) {
            if (!returnedContentVersion.equals(subject.getContentVersion())
                    || !subjectIds.add(subject.getSubjectId())) {
                throw new IllegalArgumentException("candidate subjects are inconsistent");
            }
            if (!executedScope.contains(subjectReference(subject, returnedContentVersion))) {
                throw new IllegalArgumentException("candidate subject is outside authorized scope");
            }
            if (subject.getCandidates().size() > MAX_UNITS_PER_SUBJECT) {
                throw new IllegalArgumentException("LIMIT_EXCEEDED_SUBJECT_UNITS");
            }
            unitCount += subject.getCandidates().size();
        }
        if (unitCount > MAX_UNITS) throw new IllegalArgumentException("LIMIT_EXCEEDED_UNITS");
    }

    public String getCapabilityId() { return capabilityId; }
    public int getAttempt() { return attempt; }
    public String getReturnedContentVersion() { return returnedContentVersion; }
    public AuthorizedSubjectScope getExecutedScope() { return executedScope; }
    public List<CandidateSubject> getCandidateSubjects() { return candidateSubjects; }
    public CandidateCoverageReport getCoverageReport() { return coverageReport; }

    public int getEvidenceUnitCount() {
        return candidateSubjects.stream().mapToInt(value -> value.getCandidates().size()).sum();
    }

    @Override
    public String toString() {
        return "PortfolioRetrievalCandidateSet{attempt=" + attempt
                + ", subjectCount=" + candidateSubjects.size()
                + ", evidenceUnitCount=" + getEvidenceUnitCount() + '}';
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private static com.portfolio.agent.answer.routing.domain.SubjectReference subjectReference(
            CandidateSubject subject, String contentVersion) {
        if (subject.getSubjectRoute().startsWith("/projects/")) {
            return com.portfolio.agent.answer.routing.domain.SubjectReference.project(
                    subject.getSubjectId(), contentVersion);
        }
        if (subject.getSubjectRoute().startsWith("/cases/")) {
            return com.portfolio.agent.answer.routing.domain.SubjectReference.caseReference(
                    subject.getSubjectId(), contentVersion);
        }
        throw new IllegalArgumentException("candidate subject route is unsupported");
    }

    private static final class PortfolioCapabilityId {
        private PortfolioCapabilityId() { }
        private static boolean isSupported(String value) {
            return "PORTFOLIO_EVIDENCE_RETRIEVAL_V1".equals(value);
        }
    }
}
