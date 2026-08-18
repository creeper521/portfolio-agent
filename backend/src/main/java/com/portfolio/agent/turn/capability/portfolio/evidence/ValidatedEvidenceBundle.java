package com.portfolio.agent.turn.capability.portfolio.evidence;

import com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope;

import java.util.List;
import java.util.Objects;

public final class ValidatedEvidenceBundle {
    private final AuthorizedSubjectScope scope;
    private final String contentReleaseId;
    private final List<ValidatedEvidenceUnit> units;
    public ValidatedEvidenceBundle(
            AuthorizedSubjectScope scope, String contentReleaseId,
            List<ValidatedEvidenceUnit> units) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.contentReleaseId = Objects.requireNonNull(contentReleaseId, "contentReleaseId");
        this.units = List.copyOf(Objects.requireNonNull(units, "units"));
        if (!contentReleaseId.equals(scope.getContentReleaseId())) {
            throw new IllegalArgumentException("bundle release mismatch");
        }
    }
    public AuthorizedSubjectScope getScope() { return scope; }
    public String getContentReleaseId() { return contentReleaseId; }
    public List<ValidatedEvidenceUnit> getUnits() { return units; }
}
