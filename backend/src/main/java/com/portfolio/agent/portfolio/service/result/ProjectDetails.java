package com.portfolio.agent.portfolio.service.result;

import com.portfolio.agent.portfolio.domain.EvidenceRecord;
import com.portfolio.agent.portfolio.domain.ProjectProfile;

import java.util.List;
import java.util.Objects;

public final class ProjectDetails {

    private final ProjectProfile project;
    private final List<EvidenceRecord> evidence;
    private final List<String> suggestedQuestions;

    public ProjectDetails(
            ProjectProfile project,
            List<EvidenceRecord> evidence,
            List<String> suggestedQuestions
    ) {
        this.project = project;
        this.evidence = List.copyOf(evidence);
        this.suggestedQuestions = List.copyOf(suggestedQuestions);
    }

    public ProjectProfile getProject() {
        return project;
    }

    public List<EvidenceRecord> getEvidence() {
        return evidence;
    }

    public List<String> getSuggestedQuestions() {
        return suggestedQuestions;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProjectDetails that)) {
            return false;
        }
        return Objects.equals(project, that.project)
                && Objects.equals(evidence, that.evidence)
                && Objects.equals(suggestedQuestions, that.suggestedQuestions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(project, evidence, suggestedQuestions);
    }

    @Override
    public String toString() {
        return "ProjectDetails{" +
                "project=" + project +
                ", evidence=" + evidence +
                ", suggestedQuestions=" + suggestedQuestions +
                '}';
    }
}
