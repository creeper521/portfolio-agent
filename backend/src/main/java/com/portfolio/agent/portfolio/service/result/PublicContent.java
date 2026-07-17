package com.portfolio.agent.portfolio.service.result;

import com.portfolio.agent.portfolio.domain.EvidenceRecord;
import com.portfolio.agent.portfolio.domain.OwnerProfile;
import com.portfolio.agent.portfolio.domain.TimelineEvent;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PublicContent {

    private final String contentVersion;
    private final OffsetDateTime publishedAt;
    private final OwnerProfile owner;
    private final List<ProjectDetails> projects;
    private final List<EvidenceRecord> evidence;
    private final List<TimelineEvent> timeline;
    private final Map<String, List<String>> projectSlugsByEvidenceId;

    public PublicContent(
            String contentVersion,
            OffsetDateTime publishedAt,
            OwnerProfile owner,
            List<ProjectDetails> projects,
            List<EvidenceRecord> evidence,
            List<TimelineEvent> timeline,
            Map<String, List<String>> projectSlugsByEvidenceId
    ) {
        this.contentVersion = contentVersion;
        this.publishedAt = publishedAt;
        this.owner = owner;
        this.projects = List.copyOf(projects);
        this.evidence = List.copyOf(evidence);
        this.timeline = List.copyOf(timeline);
        LinkedHashMap<String, List<String>> copiedProjectSlugs = new LinkedHashMap<>();
        projectSlugsByEvidenceId.forEach(
                (evidenceId, projectSlugs) ->
                        copiedProjectSlugs.put(evidenceId, List.copyOf(projectSlugs))
        );
        this.projectSlugsByEvidenceId = Map.copyOf(copiedProjectSlugs);
    }

    public String getContentVersion() {
        return contentVersion;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public OwnerProfile getOwner() {
        return owner;
    }

    public List<ProjectDetails> getProjects() {
        return projects;
    }

    public List<EvidenceRecord> getEvidence() {
        return evidence;
    }

    public List<TimelineEvent> getTimeline() {
        return timeline;
    }

    public Map<String, List<String>> getProjectSlugsByEvidenceId() {
        return projectSlugsByEvidenceId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PublicContent that)) {
            return false;
        }
        return Objects.equals(contentVersion, that.contentVersion)
                && Objects.equals(publishedAt, that.publishedAt)
                && Objects.equals(owner, that.owner)
                && Objects.equals(projects, that.projects)
                && Objects.equals(evidence, that.evidence)
                && Objects.equals(timeline, that.timeline)
                && Objects.equals(projectSlugsByEvidenceId, that.projectSlugsByEvidenceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentVersion, publishedAt, owner, projects, evidence, timeline,
                projectSlugsByEvidenceId);
    }

    @Override
    public String toString() {
        return "PublicContent{" +
                "contentVersion='" + contentVersion + '\'' +
                ", publishedAt=" + publishedAt +
                ", owner=" + owner +
                ", projects=" + projects +
                ", evidence=" + evidence +
                ", timeline=" + timeline +
                ", projectSlugsByEvidenceId=" + projectSlugsByEvidenceId +
                '}';
    }
}
