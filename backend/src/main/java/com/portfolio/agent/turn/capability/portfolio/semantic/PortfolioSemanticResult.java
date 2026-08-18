package com.portfolio.agent.turn.capability.portfolio.semantic;

import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit;
import com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope;
import com.portfolio.agent.turn.execution.TaskSemanticResult;

import java.util.List;
import java.util.Objects;

public abstract sealed class PortfolioSemanticResult implements TaskSemanticResult
        permits PortfolioSemanticResult.Fact,
        PortfolioSemanticResult.Comparison,
        PortfolioSemanticResult.Recommendation {
    private final Coverage coverage;
    private final AuthorizedSubjectScope authorizedSubjectScope;
    private final List<ValidatedEvidenceUnit> units;
    private final List<String> omissions;

    protected PortfolioSemanticResult(
            Coverage coverage, AuthorizedSubjectScope authorizedSubjectScope,
            List<ValidatedEvidenceUnit> units, List<String> omissions) {
        this.coverage = Objects.requireNonNull(coverage, "coverage");
        this.authorizedSubjectScope = Objects.requireNonNull(authorizedSubjectScope, "authorizedSubjectScope");
        this.units = List.copyOf(Objects.requireNonNull(units, "units"));
        this.omissions = List.copyOf(Objects.requireNonNull(omissions, "omissions"));
        if (this.units.isEmpty()) throw new IllegalArgumentException("semantic result requires support");
        if (coverage == Coverage.FULL && !this.omissions.isEmpty()) {
            throw new IllegalArgumentException("full result cannot contain omissions");
        }
    }

    public Coverage getCoverage() { return coverage; }
    public AuthorizedSubjectScope getAuthorizedSubjectScope() { return authorizedSubjectScope; }
    public List<ValidatedEvidenceUnit> getUnits() { return units; }
    public List<String> getOmissions() { return omissions; }
    public enum Coverage { FULL, PARTIAL }

    public static final class Fact extends PortfolioSemanticResult {
        public Fact(Coverage coverage, AuthorizedSubjectScope authorizedSubjectScope,
                    List<ValidatedEvidenceUnit> units, List<String> omissions) {
            super(coverage, authorizedSubjectScope, units, omissions);
        }
    }
    public static final class Comparison extends PortfolioSemanticResult {
        public Comparison(Coverage coverage, AuthorizedSubjectScope authorizedSubjectScope,
                          List<ValidatedEvidenceUnit> units, List<String> omissions) {
            super(coverage, authorizedSubjectScope, units, omissions);
        }
    }
    public static final class Recommendation extends PortfolioSemanticResult {
        private final int requestedSize;
        private final List<String> selectedSubjectIds;
        public Recommendation(
                Coverage coverage, AuthorizedSubjectScope authorizedSubjectScope,
                List<ValidatedEvidenceUnit> units, List<String> omissions,
                int requestedSize, List<String> selectedSubjectIds) {
            super(coverage, authorizedSubjectScope, units, omissions);
            this.requestedSize = requestedSize;
            this.selectedSubjectIds = List.copyOf(selectedSubjectIds);
            if (requestedSize < 1 || selectedSubjectIds.isEmpty()
                    || selectedSubjectIds.size() > requestedSize) {
                throw new IllegalArgumentException("recommendation size is invalid");
            }
        }
        public int getRequestedSize() { return requestedSize; }
        public List<String> getSelectedSubjectIds() { return selectedSubjectIds; }
    }
}
