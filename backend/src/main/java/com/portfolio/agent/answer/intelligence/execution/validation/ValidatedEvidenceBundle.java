package com.portfolio.agent.answer.intelligence.execution.validation;

import com.portfolio.agent.answer.intelligence.execution.domain.AuthorizedSubjectScope;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Pure validated evidence material; raw candidates and retrieval metadata do not survive. */
public final class ValidatedEvidenceBundle {
    private final AuthorizedSubjectScope authorizedScope;
    private final String contentVersion;
    private final List<ValidatedEvidenceUnit> units;

    public ValidatedEvidenceBundle(
            AuthorizedSubjectScope authorizedScope, String contentVersion,
            List<ValidatedEvidenceUnit> units) {
        this.authorizedScope = Objects.requireNonNull(authorizedScope, "authorizedScope");
        this.contentVersion = requireText(contentVersion, "contentVersion");
        this.units = List.copyOf(Objects.requireNonNull(units, "units"));
        if (!contentVersion.equals(authorizedScope.getContentVersion())) {
            throw new IllegalArgumentException("bundle content version conflicts with scope");
        }
        LinkedHashSet<String> claimEvidencePairs = new LinkedHashSet<>();
        for (ValidatedEvidenceUnit unit : this.units) {
            if (!authorizedScope.contains(subjectReference(unit, contentVersion))) {
                throw new IllegalArgumentException("validated unit is outside authorized scope");
            }
            String claimEvidencePair = unit.getClaimId()
                    + "\u0000" + unit.getSourceReference().getReferenceKey();
            if (!claimEvidencePairs.add(claimEvidencePair)) {
                throw new IllegalArgumentException("claim and evidence pair must be unique");
            }
        }
    }

    public AuthorizedSubjectScope getAuthorizedScope() { return authorizedScope; }
    public String getContentVersion() { return contentVersion; }
    public List<ValidatedEvidenceUnit> getUnits() { return units; }
    public int getAcceptedUnitCount() { return units.size(); }

    @Override public String toString() { return "ValidatedEvidenceBundle{unitCount=" + units.size() + '}'; }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private static com.portfolio.agent.answer.routing.domain.SubjectReference subjectReference(
            ValidatedEvidenceUnit unit, String contentVersion) {
        String route = unit.getSourceReference().getSubjectRoute();
        if (route.startsWith("/projects/")) {
            return com.portfolio.agent.answer.routing.domain.SubjectReference.project(
                    unit.getSubjectId(), contentVersion);
        }
        if (route.startsWith("/cases/")) {
            return com.portfolio.agent.answer.routing.domain.SubjectReference.caseReference(
                    unit.getSubjectId(), contentVersion);
        }
        throw new IllegalArgumentException("validated unit subject route is unsupported");
    }
}
