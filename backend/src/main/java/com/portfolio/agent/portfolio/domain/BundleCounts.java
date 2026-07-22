package com.portfolio.agent.portfolio.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public final class BundleCounts {
    private final int projects;
    private final int claims;
    private final int evidence;
    private final int claimEvidenceLinks;
    private final int timelineEvents;
    private final int questionPresets;

    @JsonCreator
    public BundleCounts(@JsonProperty("projects") int projects,
            @JsonProperty("claims") int claims,
            @JsonProperty("evidence") int evidence,
            @JsonProperty("claimEvidenceLinks") int claimEvidenceLinks,
            @JsonProperty("timelineEvents") int timelineEvents,
            @JsonProperty("questionPresets") int questionPresets) {
        this.projects = projects;
        this.claims = claims;
        this.evidence = evidence;
        this.claimEvidenceLinks = claimEvidenceLinks;
        this.timelineEvents = timelineEvents;
        this.questionPresets = questionPresets;
    }
    public int getProjects() { return projects; }
    public int getClaims() { return claims; }
    public int getEvidence() { return evidence; }
    public int getClaimEvidenceLinks() { return claimEvidenceLinks; }
    public int getTimelineEvents() { return timelineEvents; }
    public int getQuestionPresets() { return questionPresets; }
    public boolean matches(PortfolioSnapshot value) {
        return projects == value.getProjects().size() && claims == value.getClaims().size()
                && evidence == value.getEvidence().size()
                && claimEvidenceLinks == value.getClaimEvidenceLinks().size()
                && timelineEvents == value.getTimeline().size()
                && questionPresets == value.getQuestions().size();
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BundleCounts that)) return false;
        return projects == that.projects && claims == that.claims && evidence == that.evidence
                && claimEvidenceLinks == that.claimEvidenceLinks
                && timelineEvents == that.timelineEvents && questionPresets == that.questionPresets;
    }
    @Override public int hashCode() {
        return Objects.hash(projects, claims, evidence, claimEvidenceLinks,
                timelineEvents, questionPresets);
    }
}
