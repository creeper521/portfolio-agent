package com.portfolio.agent.answer.intelligence.domain;

import java.util.Objects;
import java.util.Set;

public final class PortfolioRetrievedSubject {

    private final String portfolioId;
    private final String subjectType;
    private final String title;
    private final String summary;
    private final String route;
    private final Set<String> capabilityCodes;

    public PortfolioRetrievedSubject(
            String portfolioId,
            String subjectType,
            String title,
            String summary,
            String route,
            Set<String> capabilityCodes) {
        this.portfolioId = requireText(portfolioId, "portfolioId");
        this.subjectType = requireText(subjectType, "subjectType");
        this.title = requireText(title, "title");
        this.summary = requireText(summary, "summary");
        this.route = requireText(route, "route");
        this.capabilityCodes = Set.copyOf(Objects.requireNonNull(capabilityCodes, "capabilityCodes"));
    }

    public String getPortfolioId() { return portfolioId; }
    public String getSubjectId() { return portfolioId; }
    public String getSubjectType() { return subjectType; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getRoute() { return route; }
    public Set<String> getCapabilityCodes() { return capabilityCodes; }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof PortfolioRetrievedSubject that)) { return false; }
        return Objects.equals(portfolioId, that.portfolioId)
                && Objects.equals(subjectType, that.subjectType)
                && Objects.equals(title, that.title)
                && Objects.equals(summary, that.summary)
                && Objects.equals(route, that.route)
                && Objects.equals(capabilityCodes, that.capabilityCodes);
    }

    @Override
    public int hashCode() { return Objects.hash(portfolioId, subjectType, title, summary, route, capabilityCodes); }

    @Override
    public String toString() {
        return "PortfolioRetrievedSubject{" + "portfolioId='" + portfolioId + '\''
                + ", subjectType='" + subjectType + '\'' + ", title='" + title + '\'' + '}';
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) { throw new IllegalArgumentException(fieldName + " is required"); }
        return value.trim();
    }
}
