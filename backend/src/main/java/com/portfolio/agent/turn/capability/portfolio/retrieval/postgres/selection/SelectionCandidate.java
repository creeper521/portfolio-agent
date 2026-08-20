package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection;

import java.util.Objects;
import java.util.List;
import java.util.Set;

public final class SelectionCandidate {

    private final String subjectId;
    private final PortfolioSubjectKind subjectKind;
    private final String title;
    private final String summary;
    private final String route;
    private final String careerTrack;
    private final Set<String> capabilityCodes;
    private final List<EvidenceReference> evidenceReferences;
    private final double targetFit;
    private final double evidenceQuality;
    private final double conflictPenalty;

    public SelectionCandidate(
            String subjectId,
            PortfolioSubjectKind subjectKind,
            String careerTrack,
            Set<String> capabilityCodes,
            double targetFit,
            double evidenceQuality,
            double conflictPenalty) {
        this(
                subjectId,
                subjectKind,
                subjectId,
                subjectId,
                defaultRoute(subjectKind, subjectId),
                careerTrack,
                capabilityCodes,
                List.of(),
                targetFit,
                evidenceQuality,
                conflictPenalty);
    }

    public SelectionCandidate(
            String subjectId,
            PortfolioSubjectKind subjectKind,
            String title,
            String summary,
            String route,
            String careerTrack,
            Set<String> capabilityCodes,
            List<EvidenceReference> evidenceReferences,
            double targetFit,
            double evidenceQuality,
            double conflictPenalty) {
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId");
        this.subjectKind = Objects.requireNonNull(subjectKind, "subjectKind");
        this.title = requireText(title, "title");
        this.summary = requireText(summary, "summary");
        this.route = requireText(route, "route");
        this.careerTrack = careerTrack;
        this.capabilityCodes = Set.copyOf(Objects.requireNonNull(capabilityCodes, "capabilityCodes"));
        this.evidenceReferences = List.copyOf(
                Objects.requireNonNull(evidenceReferences, "evidenceReferences"));
        this.targetFit = bounded(targetFit, "targetFit");
        this.evidenceQuality = bounded(evidenceQuality, "evidenceQuality");
        this.conflictPenalty = nonNegative(conflictPenalty, "conflictPenalty");
    }

    public String getSubjectId() {
        return subjectId;
    }

    public PortfolioSubjectKind getSubjectKind() {
        return subjectKind;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getRoute() {
        return route;
    }

    public String getCareerTrack() {
        return careerTrack;
    }

    public Set<String> getCapabilityCodes() {
        return capabilityCodes;
    }

    public List<EvidenceReference> getEvidenceReferences() {
        return evidenceReferences;
    }

    public double getTargetFit() {
        return targetFit;
    }

    public double getEvidenceQuality() {
        return evidenceQuality;
    }

    public double getConflictPenalty() {
        return conflictPenalty;
    }

    private double bounded(double value, String name) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
        return value;
    }

    private double nonNegative(double value, String name) {
        if (value < 0.0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return value;
    }

    private String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String defaultRoute(PortfolioSubjectKind kind, String subjectId) {
        return (kind == PortfolioSubjectKind.PROJECT ? "/projects/" : "/cases/")
                + subjectId.toLowerCase(java.util.Locale.ROOT);
    }
}
