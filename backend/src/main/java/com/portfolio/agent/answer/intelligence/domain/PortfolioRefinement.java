package com.portfolio.agent.answer.intelligence.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class PortfolioRefinement {

    private final PortfolioConditions conditions;
    private final Set<String> excludedPortfolioIds;

    @JsonCreator
    public PortfolioRefinement(
            @JsonProperty("conditions") PortfolioConditions conditions,
            @JsonProperty("excludedPortfolioIds") Set<String> excludedPortfolioIds) {
        this.conditions = Objects.requireNonNull(conditions, "conditions");
        this.excludedPortfolioIds = Set.copyOf(new LinkedHashSet<>(
                Objects.requireNonNull(excludedPortfolioIds, "excludedPortfolioIds")));
    }

    public PortfolioConditions getConditions() { return conditions; }
    public Set<String> getExcludedPortfolioIds() { return excludedPortfolioIds; }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof PortfolioRefinement that)) { return false; }
        return Objects.equals(conditions, that.conditions)
                && Objects.equals(excludedPortfolioIds, that.excludedPortfolioIds);
    }

    @Override
    public int hashCode() { return Objects.hash(conditions, excludedPortfolioIds); }

    @Override
    public String toString() {
        return "PortfolioRefinement{" + "conditions=" + conditions
                + ", excludedPortfolioCount=" + excludedPortfolioIds.size() + '}';
    }
}
