package com.portfolio.agent.answer.intelligence.domain;

import java.util.Objects;
import java.util.Set;

public final class PortfolioRetrievedSubject {

    private final String portfolioId;
    private final String subjectType;
    private final String title;
    private final String summary;
    private final String route;
    private final String careerTrack;
    private final Set<String> capabilityCodes;
    private final double targetFit;
    private final double evidenceQuality;
    private final double conflictPenalty;

    public PortfolioRetrievedSubject(
            String portfolioId,
            String subjectType,
            String title,
            String summary,
            String route,
            Set<String> capabilityCodes) {
        this(portfolioId, subjectType, title, summary, route, null, capabilityCodes,
                0.0d, 0.0d, 0.0d);
    }

    public PortfolioRetrievedSubject(
            String portfolioId,
            String subjectType,
            String title,
            String summary,
            String route,
            String careerTrack,
            Set<String> capabilityCodes) {
        this(portfolioId, subjectType, title, summary, route, careerTrack, capabilityCodes,
                0.0d, 0.0d, 0.0d);
    }

    public PortfolioRetrievedSubject(
            String portfolioId,
            String subjectType,
            String title,
            String summary,
            String route,
            String careerTrack,
            Set<String> capabilityCodes,
            double targetFit,
            double evidenceQuality,
            double conflictPenalty) {
        this.portfolioId = requireText(portfolioId, "portfolioId");
        this.subjectType = requireText(subjectType, "subjectType");
        this.title = requireText(title, "title");
        this.summary = requireText(summary, "summary");
        this.route = requireText(route, "route");
        this.careerTrack = normalizeControlledValue(careerTrack);
        this.capabilityCodes = Set.copyOf(Objects.requireNonNull(capabilityCodes, "capabilityCodes"));
        this.targetFit = bounded(targetFit, "targetFit");
        this.evidenceQuality = bounded(evidenceQuality, "evidenceQuality");
        this.conflictPenalty = nonNegative(conflictPenalty, "conflictPenalty");
    }

    public String getPortfolioId() { return portfolioId; }
    public String getSubjectId() { return portfolioId; }
    public String getSubjectType() { return subjectType; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getRoute() { return route; }
    public String getCareerTrack() { return careerTrack; }
    public Set<String> getCapabilityCodes() { return capabilityCodes; }
    public double getTargetFit() { return targetFit; }
    public double getEvidenceQuality() { return evidenceQuality; }
    public double getConflictPenalty() { return conflictPenalty; }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof PortfolioRetrievedSubject that)) { return false; }
        return Objects.equals(portfolioId, that.portfolioId)
                && Objects.equals(subjectType, that.subjectType)
                && Objects.equals(title, that.title)
                && Objects.equals(summary, that.summary)
                && Objects.equals(route, that.route)
                && Objects.equals(careerTrack, that.careerTrack)
                && Objects.equals(capabilityCodes, that.capabilityCodes)
                && Double.compare(targetFit, that.targetFit) == 0
                && Double.compare(evidenceQuality, that.evidenceQuality) == 0
                && Double.compare(conflictPenalty, that.conflictPenalty) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(portfolioId, subjectType, title, summary, route, careerTrack,
                capabilityCodes, targetFit, evidenceQuality, conflictPenalty);
    }

    @Override
    public String toString() {
        return "PortfolioRetrievedSubject{" + "portfolioId='" + portfolioId + '\''
                + ", subjectType='" + subjectType + '\'' + ", title='" + title + '\'' + '}';
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) { throw new IllegalArgumentException(fieldName + " is required"); }
        return value.trim();
    }

    private static String normalizeControlledValue(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static double bounded(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
        return value;
    }

    private static double nonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0d) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return value;
    }
}
