package com.portfolio.agent.turn.capability.portfolio.semantic;

import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit;
import com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope;
import com.portfolio.agent.turn.execution.TaskSemanticResult;
import com.portfolio.agent.turn.planning.UserGoalProposal;

import java.util.List;
import java.util.Objects;
import java.util.Set;

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
        private final UserGoalProposal.Depth depth;
        public Fact(Coverage coverage, AuthorizedSubjectScope authorizedSubjectScope,
                    List<ValidatedEvidenceUnit> units, List<String> omissions,
                    UserGoalProposal.Depth depth) {
            super(coverage, authorizedSubjectScope, units, omissions);
            this.depth = Objects.requireNonNull(depth, "depth");
        }
        public Fact(Coverage coverage, AuthorizedSubjectScope authorizedSubjectScope,
                    List<ValidatedEvidenceUnit> units, List<String> omissions) {
            this(coverage, authorizedSubjectScope, units, omissions,
                    UserGoalProposal.Depth.STANDARD);
        }
        public UserGoalProposal.Depth getDepth() { return depth; }
    }
    public static final class Comparison extends PortfolioSemanticResult {
        private final List<UserGoalProposal.PortfolioComparisonDimension> dimensions;
        public Comparison(Coverage coverage, AuthorizedSubjectScope authorizedSubjectScope,
                          List<ValidatedEvidenceUnit> units, List<String> omissions,
                          List<UserGoalProposal.PortfolioComparisonDimension> dimensions) {
            super(coverage, authorizedSubjectScope, units, omissions);
            this.dimensions = List.copyOf(Objects.requireNonNull(dimensions, "dimensions"));
            if (this.dimensions.isEmpty()) {
                throw new IllegalArgumentException("comparison dimensions are required");
            }
        }
        public Comparison(Coverage coverage, AuthorizedSubjectScope authorizedSubjectScope,
                          List<ValidatedEvidenceUnit> units, List<String> omissions) {
            this(coverage, authorizedSubjectScope, units, omissions,
                    List.of(UserGoalProposal.PortfolioComparisonDimension.IMPLEMENTATION));
        }
        public List<UserGoalProposal.PortfolioComparisonDimension> getDimensions() {
            return dimensions;
        }
    }
    public static final class Recommendation extends PortfolioSemanticResult {
        private final int requestedSize;
        private final List<String> selectedSubjectIds;
        private final List<RecommendationItem> items;
        private final List<String> unsatisfiedConstraints;
        public Recommendation(
                Coverage coverage, AuthorizedSubjectScope authorizedSubjectScope,
                List<ValidatedEvidenceUnit> units, List<String> omissions,
                int requestedSize, List<String> selectedSubjectIds) {
            this(coverage, authorizedSubjectScope, units, omissions, requestedSize,
                    selectedSubjectIds.stream().map(subjectId -> new RecommendationItem(
                            subjectId, List.of(RecommendationReasonCode.VERIFIED_PUBLIC_EVIDENCE)))
                            .toList(), List.of());
        }
        public Recommendation(
                Coverage coverage, AuthorizedSubjectScope authorizedSubjectScope,
                List<ValidatedEvidenceUnit> units, List<String> omissions,
                int requestedSize, List<RecommendationItem> items,
                List<String> unsatisfiedConstraints) {
            super(coverage, authorizedSubjectScope, units, omissions);
            this.requestedSize = requestedSize;
            this.items = List.copyOf(Objects.requireNonNull(items, "items"));
            this.selectedSubjectIds = this.items.stream()
                    .map(RecommendationItem::subjectId).toList();
            this.unsatisfiedConstraints = List.copyOf(Objects.requireNonNull(
                    unsatisfiedConstraints, "unsatisfiedConstraints"));
            if (requestedSize < 1 || this.items.isEmpty()
                    || this.items.size() > requestedSize
                    || this.selectedSubjectIds.stream().distinct().count()
                    != this.selectedSubjectIds.size()) {
                throw new IllegalArgumentException("recommendation size is invalid");
            }
            if (coverage == Coverage.FULL && !this.unsatisfiedConstraints.isEmpty()) {
                throw new IllegalArgumentException("full recommendation cannot miss constraints");
            }
        }
        public int getRequestedSize() { return requestedSize; }
        public List<String> getSelectedSubjectIds() { return selectedSubjectIds; }
        public List<RecommendationItem> getItems() { return items; }
        public List<String> getUnsatisfiedConstraints() { return unsatisfiedConstraints; }

        public record RecommendationItem(
                String subjectId, List<RecommendationReasonCode> reasonCodes) {
            public RecommendationItem {
                Objects.requireNonNull(subjectId, "subjectId");
                reasonCodes = List.copyOf(Objects.requireNonNull(reasonCodes, "reasonCodes"));
                if (reasonCodes.isEmpty()) {
                    throw new IllegalArgumentException("recommendation reasons are required");
                }
            }
        }

        public enum RecommendationReasonCode {
            CAREER_TRACK_MATCH,
            CAPABILITY_MATCH,
            VERIFIED_IMPLEMENTATION,
            VERIFIED_VERIFICATION,
            VERIFIED_OUTCOME,
            VERIFIED_PUBLIC_EVIDENCE
        }
    }
}
