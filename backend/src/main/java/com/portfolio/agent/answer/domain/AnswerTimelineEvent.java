package com.portfolio.agent.answer.domain;

import java.util.List;
import java.util.Objects;

public final class AnswerTimelineEvent {

    private final String id;
    private final String dateLabel;
    private final String title;
    private final String problem;
    private final String action;
    private final String impact;
    private final List<String> projectSlugs;
    private final List<String> claimIds;
    private final List<String> evidenceIds;

    public AnswerTimelineEvent(
            String id,
            String dateLabel,
            String title,
            String problem,
            String action,
            String impact,
            List<String> projectSlugs,
            List<String> claimIds,
            List<String> evidenceIds
    ) {
        this.id = id;
        this.dateLabel = dateLabel;
        this.title = title;
        this.problem = problem;
        this.action = action;
        this.impact = impact;
        this.projectSlugs = List.copyOf(projectSlugs);
        this.claimIds = List.copyOf(claimIds);
        this.evidenceIds = List.copyOf(evidenceIds);
    }

    public String getId() { return id; }
    public String getDateLabel() { return dateLabel; }
    public String getTitle() { return title; }
    public String getProblem() { return problem; }
    public String getAction() { return action; }
    public String getImpact() { return impact; }
    public List<String> getProjectSlugs() { return projectSlugs; }
    public List<String> getClaimIds() { return claimIds; }
    public List<String> getEvidenceIds() { return evidenceIds; }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AnswerTimelineEvent that)) {
            return false;
        }
        return Objects.equals(id, that.id)
                && Objects.equals(dateLabel, that.dateLabel)
                && Objects.equals(title, that.title)
                && Objects.equals(problem, that.problem)
                && Objects.equals(action, that.action)
                && Objects.equals(impact, that.impact)
                && Objects.equals(projectSlugs, that.projectSlugs)
                && Objects.equals(claimIds, that.claimIds)
                && Objects.equals(evidenceIds, that.evidenceIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, dateLabel, title, problem, action, impact,
                projectSlugs, claimIds, evidenceIds);
    }
}
