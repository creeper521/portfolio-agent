package com.portfolio.agent.answer.intelligence.execution.domain;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.ComparisonDimension;

import java.util.List;
import java.util.Objects;

/** Versioned closed comparison profile for one P2 dimension. */
public final class ComparisonDimensionProfile {

    private final ComparisonDimension dimension;
    private final String profileId;
    private final String profileVersion;
    private final List<String> claimCategories;

    private ComparisonDimensionProfile(
            ComparisonDimension dimension, String profileId, String profileVersion,
            List<String> claimCategories) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.profileId = requireText(profileId, "profileId");
        this.profileVersion = requireText(profileVersion, "profileVersion");
        this.claimCategories = List.copyOf(Objects.requireNonNull(claimCategories, "claimCategories"));
    }

    public static ComparisonDimensionProfile forDimension(ComparisonDimension dimension) {
        Objects.requireNonNull(dimension, "dimension");
        String category = dimension == ComparisonDimension.ARCHITECTURE
                ? "TECHNICAL_DECISION" : dimension.name();
        return new ComparisonDimensionProfile(
                dimension, "PORTFOLIO_DIMENSION_" + dimension.name(), "v1", List.of(category));
    }

    public ComparisonDimension getDimension() {
        return dimension;
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
        if (!(other instanceof ComparisonDimensionProfile that)) {
            return false;
        }
        return dimension == that.dimension && profileId.equals(that.profileId)
                && profileVersion.equals(that.profileVersion)
                && claimCategories.equals(that.claimCategories);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimension, profileId, profileVersion, claimCategories);
    }

    @Override
    public String toString() {
        return "ComparisonDimensionProfile{dimension=" + dimension + ", version="
                + profileVersion + '}';
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
