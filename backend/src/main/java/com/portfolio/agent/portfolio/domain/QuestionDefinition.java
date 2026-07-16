package com.portfolio.agent.portfolio.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public final class QuestionDefinition {

    private final String id;
    private final String projectId;
    private final String canonicalQuestion;
    private final List<String> aliases;
    private final String suggestion;

    @JsonCreator
    public QuestionDefinition(
            @JsonProperty("id") String id,
            @JsonProperty("projectId") String projectId,
            @JsonProperty("canonicalQuestion") String canonicalQuestion,
            @JsonProperty("aliases") List<String> aliases,
            @JsonProperty("suggestion") String suggestion
    ) {
        this.id = id;
        this.projectId = projectId;
        this.canonicalQuestion = canonicalQuestion;
        this.aliases = List.copyOf(aliases);
        this.suggestion = suggestion;
    }

    public String getId() {
        return id;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getCanonicalQuestion() {
        return canonicalQuestion;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public String getSuggestion() {
        return suggestion;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof QuestionDefinition that)) {
            return false;
        }
        return Objects.equals(id, that.id)
                && Objects.equals(projectId, that.projectId)
                && Objects.equals(canonicalQuestion, that.canonicalQuestion)
                && Objects.equals(aliases, that.aliases)
                && Objects.equals(suggestion, that.suggestion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, projectId, canonicalQuestion, aliases, suggestion);
    }

    @Override
    public String toString() {
        return "QuestionDefinition{" +
                "id='" + id + '\'' +
                ", projectId='" + projectId + '\'' +
                ", canonicalQuestion='" + canonicalQuestion + '\'' +
                ", aliases=" + aliases +
                ", suggestion='" + suggestion + '\'' +
                '}';
    }
}
