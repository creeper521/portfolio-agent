package com.portfolio.agent.portfolio.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public final class QuestionDefinition {

    private final String id;
    private final String text;
    private final List<String> aliases;
    private final List<String> audiences;
    private final List<String> projectIds;
    private final List<String> caseIds;
    private final List<String> topics;
    private final List<ClaimCategory> preferredClaimCategories;
    private final List<String> placements;
    private final boolean deterministicEntry;
    private final int displayOrder;

    @JsonCreator
    public QuestionDefinition(
            @JsonProperty("id") String id,
            @JsonProperty("text") String text,
            @JsonProperty("aliases") List<String> aliases,
            @JsonProperty("audiences") List<String> audiences,
            @JsonProperty("projectIds") List<String> projectIds,
            @JsonProperty("caseIds") List<String> caseIds,
            @JsonProperty("topics") List<String> topics,
            @JsonProperty("preferredClaimCategories") List<ClaimCategory> preferredClaimCategories,
            @JsonProperty("placements") List<String> placements,
            @JsonProperty("deterministicEntry") boolean deterministicEntry,
            @JsonProperty("displayOrder") int displayOrder
    ) {
        this.id = id;
        this.text = text;
        this.aliases = List.copyOf(aliases);
        this.audiences = List.copyOf(audiences);
        this.projectIds = List.copyOf(projectIds);
        this.caseIds = List.copyOf(caseIds);
        this.topics = List.copyOf(topics);
        this.preferredClaimCategories = List.copyOf(preferredClaimCategories);
        this.placements = List.copyOf(placements);
        this.deterministicEntry = deterministicEntry;
        this.displayOrder = displayOrder;
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public List<String> getAudiences() {
        return audiences;
    }

    public List<String> getProjectIds() { return projectIds; }

    public List<String> getCaseIds() { return caseIds; }

    public List<String> getTopics() { return topics; }

    public List<ClaimCategory> getPreferredClaimCategories() { return preferredClaimCategories; }

    public List<String> getPlacements() {
        return placements;
    }

    public boolean isDeterministicEntry() { return deterministicEntry; }

    public int getDisplayOrder() { return displayOrder; }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof QuestionDefinition that)) {
            return false;
        }
        return Objects.equals(id, that.id)
                && Objects.equals(text, that.text)
                && Objects.equals(aliases, that.aliases)
                && Objects.equals(audiences, that.audiences)
                && Objects.equals(projectIds, that.projectIds)
                && Objects.equals(caseIds, that.caseIds)
                && Objects.equals(topics, that.topics)
                && Objects.equals(preferredClaimCategories, that.preferredClaimCategories)
                && Objects.equals(placements, that.placements)
                && deterministicEntry == that.deterministicEntry
                && displayOrder == that.displayOrder;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, text, aliases, audiences, projectIds, caseIds, topics,
                preferredClaimCategories, placements, deterministicEntry, displayOrder);
    }

    @Override
    public String toString() {
        return "QuestionDefinition{" +
                "id='" + id + '\'' +
                ", text='" + text + '\'' +
                ", aliases=" + aliases +
                ", projectIds=" + projectIds +
                ", caseIds=" + caseIds +
                '}';
    }
}
