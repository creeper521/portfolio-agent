package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection;

import java.util.Objects;
import java.util.List;
import java.util.Set;

public final class PostgresSelectionRow {

    private final String subjectId;
    private final PortfolioSubjectKind subjectKind;
    private final String title;
    private final String summary;
    private final String route;
    private final String careerTrack;
    private final Set<String> capabilityCodes;
    private final List<EvidenceReference> evidenceReferences;
    private final double evidenceQuality;

    public PostgresSelectionRow(
            String subjectId,
            PortfolioSubjectKind subjectKind,
            String careerTrack,
            Set<String> capabilityCodes,
            double evidenceQuality) {
        this(
                subjectId,
                subjectKind,
                subjectId,
                subjectId,
                (subjectKind == PortfolioSubjectKind.PROJECT ? "/projects/" : "/cases/")
                        + subjectId.toLowerCase(java.util.Locale.ROOT),
                careerTrack,
                capabilityCodes,
                List.of(),
                evidenceQuality);
    }

    public PostgresSelectionRow(
            String subjectId,
            PortfolioSubjectKind subjectKind,
            String title,
            String summary,
            String route,
            String careerTrack,
            Set<String> capabilityCodes,
            List<EvidenceReference> evidenceReferences,
            double evidenceQuality) {
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId");
        this.subjectKind = Objects.requireNonNull(subjectKind, "subjectKind");
        this.title = Objects.requireNonNull(title, "title");
        this.summary = Objects.requireNonNull(summary, "summary");
        this.route = Objects.requireNonNull(route, "route");
        this.careerTrack = careerTrack;
        this.capabilityCodes = Set.copyOf(Objects.requireNonNull(capabilityCodes, "capabilityCodes"));
        this.evidenceReferences = List.copyOf(
                Objects.requireNonNull(evidenceReferences, "evidenceReferences"));
        this.evidenceQuality = evidenceQuality;
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

    public double getEvidenceQuality() {
        return evidenceQuality;
    }
}
