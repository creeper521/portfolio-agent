package com.portfolio.agent.portfolio.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public final class PortfolioSnapshot {

    private final String schemaVersion;
    private final String contentVersion;
    private final OffsetDateTime publishedAt;
    private final OwnerProfile owner;
    private final List<ProjectProfile> projects;
    private final List<QuestionDefinition> questions;
    private final List<EvidenceRecord> evidence;

    @JsonCreator
    public PortfolioSnapshot(
            @JsonProperty("schemaVersion") String schemaVersion,
            @JsonProperty("contentVersion") String contentVersion,
            @JsonProperty("publishedAt") OffsetDateTime publishedAt,
            @JsonProperty("owner") OwnerProfile owner,
            @JsonProperty("projects") List<ProjectProfile> projects,
            @JsonProperty("questions") List<QuestionDefinition> questions,
            @JsonProperty("evidence") List<EvidenceRecord> evidence
    ) {
        this.schemaVersion = schemaVersion;
        this.contentVersion = contentVersion;
        this.publishedAt = publishedAt;
        this.owner = owner;
        this.projects = List.copyOf(projects);
        this.questions = List.copyOf(questions);
        this.evidence = List.copyOf(evidence);
    }

    public String getSchemaVersion() {
        return schemaVersion;
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

    public List<ProjectProfile> getProjects() {
        return projects;
    }

    public List<QuestionDefinition> getQuestions() {
        return questions;
    }

    public List<EvidenceRecord> getEvidence() {
        return evidence;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PortfolioSnapshot that)) {
            return false;
        }
        return Objects.equals(schemaVersion, that.schemaVersion)
                && Objects.equals(contentVersion, that.contentVersion)
                && Objects.equals(publishedAt, that.publishedAt)
                && Objects.equals(owner, that.owner)
                && Objects.equals(projects, that.projects)
                && Objects.equals(questions, that.questions)
                && Objects.equals(evidence, that.evidence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemaVersion, contentVersion, publishedAt, owner, projects, questions, evidence);
    }

    @Override
    public String toString() {
        return "PortfolioSnapshot{" +
                "schemaVersion='" + schemaVersion + '\'' +
                ", contentVersion='" + contentVersion + '\'' +
                ", publishedAt=" + publishedAt +
                ", owner=" + owner +
                ", projects=" + projects +
                ", questions=" + questions +
                ", evidence=" + evidence +
                '}';
    }
}
