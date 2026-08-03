package com.portfolio.agent.answer.intelligence.service;

import java.util.List;
import java.util.Objects;

public final class PortfolioReferenceResolution {

    private final PortfolioReferenceResolutionType type;
    private final List<String> subjectIds;
    private final List<String> claimIds;

    private PortfolioReferenceResolution(
            PortfolioReferenceResolutionType type,
            List<String> subjectIds,
            List<String> claimIds
    ) {
        this.type = Objects.requireNonNull(type, "type");
        this.subjectIds = List.copyOf(Objects.requireNonNull(subjectIds, "subjectIds"));
        this.claimIds = List.copyOf(Objects.requireNonNull(claimIds, "claimIds"));
    }

    public static PortfolioReferenceResolution resolved(
            PortfolioReferenceResolutionType type,
            List<String> subjectIds,
            List<String> claimIds
    ) {
        if (type != PortfolioReferenceResolutionType.VALID
                && type != PortfolioReferenceResolutionType.VERSION_UPDATED) {
            throw new IllegalArgumentException("resolved type is required");
        }
        return new PortfolioReferenceResolution(type, subjectIds, claimIds);
    }

    public static PortfolioReferenceResolution referencesMissing() {
        return new PortfolioReferenceResolution(
                PortfolioReferenceResolutionType.REFERENCES_MISSING,
                List.of(),
                List.of());
    }

    public static PortfolioReferenceResolution invalid() {
        return new PortfolioReferenceResolution(
                PortfolioReferenceResolutionType.INVALID,
                List.of(),
                List.of());
    }

    public PortfolioReferenceResolutionType getType() { return type; }
    public List<String> getSubjectIds() { return subjectIds; }
    public List<String> getClaimIds() { return claimIds; }
    public boolean isContextVersionUpdated() {
        return type == PortfolioReferenceResolutionType.VERSION_UPDATED;
    }
}
