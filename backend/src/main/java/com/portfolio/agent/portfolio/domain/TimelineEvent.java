package com.portfolio.agent.portfolio.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public final class TimelineEvent {

    private final String id;
    private final String dateLabel;
    private final String title;
    private final String problem;
    private final String action;
    private final String impact;
    private final List<String> projectSlugs;
    private final List<String> evidenceIds;

    @JsonCreator
    public TimelineEvent(
            @JsonProperty("id") String id,
            @JsonProperty("dateLabel") String dateLabel,
            @JsonProperty("title") String title,
            @JsonProperty("problem") String problem,
            @JsonProperty("action") String action,
            @JsonProperty("impact") String impact,
            @JsonProperty("projectSlugs") List<String> projectSlugs,
            @JsonProperty("evidenceIds") List<String> evidenceIds
    ) {
        this.id = id;
        this.dateLabel = dateLabel;
        this.title = title;
        this.problem = problem;
        this.action = action;
        this.impact = impact;
        this.projectSlugs = List.copyOf(projectSlugs);
        this.evidenceIds = List.copyOf(evidenceIds);
    }

    public String getId() {
        return id;
    }

    public String getDateLabel() {
        return dateLabel;
    }

    public String getTitle() {
        return title;
    }

    public String getProblem() {
        return problem;
    }

    public String getAction() {
        return action;
    }

    public String getImpact() {
        return impact;
    }

    public List<String> getProjectSlugs() {
        return projectSlugs;
    }

    public List<String> getEvidenceIds() {
        return evidenceIds;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TimelineEvent that)) {
            return false;
        }
        return Objects.equals(id, that.id)
                && Objects.equals(dateLabel, that.dateLabel)
                && Objects.equals(title, that.title)
                && Objects.equals(problem, that.problem)
                && Objects.equals(action, that.action)
                && Objects.equals(impact, that.impact)
                && Objects.equals(projectSlugs, that.projectSlugs)
                && Objects.equals(evidenceIds, that.evidenceIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, dateLabel, title, problem, action, impact, projectSlugs, evidenceIds);
    }

    @Override
    public String toString() {
        return "TimelineEvent{" +
                "id='" + id + '\'' +
                ", dateLabel='" + dateLabel + '\'' +
                ", title='" + title + '\'' +
                ", problem='" + problem + '\'' +
                ", action='" + action + '\'' +
                ", impact='" + impact + '\'' +
                ", projectSlugs=" + projectSlugs +
                ", evidenceIds=" + evidenceIds +
                '}';
    }
}
