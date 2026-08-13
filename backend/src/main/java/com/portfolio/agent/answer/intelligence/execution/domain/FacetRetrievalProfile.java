package com.portfolio.agent.answer.intelligence.execution.domain;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.PortfolioFacet;

import java.util.List;
import java.util.Objects;

/** Versioned closed retrieval profile for one P2 facet. */
public final class FacetRetrievalProfile {

    private final PortfolioFacet facet;
    private final String profileId;
    private final String profileVersion;
    private final List<String> claimCategories;

    private FacetRetrievalProfile(
            PortfolioFacet facet, String profileId, String profileVersion, List<String> claimCategories) {
        this.facet = Objects.requireNonNull(facet, "facet");
        this.profileId = requireText(profileId, "profileId");
        this.profileVersion = requireText(profileVersion, "profileVersion");
        this.claimCategories = List.copyOf(Objects.requireNonNull(claimCategories, "claimCategories"));
    }

    public static FacetRetrievalProfile forFacet(PortfolioFacet facet) {
        Objects.requireNonNull(facet, "facet");
        List<String> categories = switch (facet) {
            case OVERVIEW -> List.of("BACKGROUND");
            case RESPONSIBILITY -> List.of("RESPONSIBILITY");
            case IMPLEMENTATION -> List.of("IMPLEMENTATION");
            case DECISION -> List.of("TECHNICAL_DECISION");
            case CHALLENGE -> List.of("LIMITATION", "REFLECTION");
            case INCIDENT -> List.of("VERIFICATION", "OUTCOME");
            case VERIFICATION -> List.of("VERIFICATION");
            case LIMITATION -> List.of("LIMITATION");
            case LEARNING -> List.of("LEARNING");
            case OUTCOME -> List.of("OUTCOME");
        };
        return new FacetRetrievalProfile(facet, "PORTFOLIO_FACET_" + facet.name(), "v1", categories);
    }

    public PortfolioFacet getFacet() {
        return facet;
    }

    public String getProfileId() {
        return profileId;
    }

    public String getProfileVersion() {
        return profileVersion;
    }

    public List<String> getClaimCategories() {
        return claimCategories;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FacetRetrievalProfile that)) {
            return false;
        }
        return facet == that.facet && profileId.equals(that.profileId)
                && profileVersion.equals(that.profileVersion)
                && claimCategories.equals(that.claimCategories);
    }

    @Override
    public int hashCode() {
        return Objects.hash(facet, profileId, profileVersion, claimCategories);
    }

    @Override
    public String toString() {
        return "FacetRetrievalProfile{facet=" + facet + ", version=" + profileVersion + '}';
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
