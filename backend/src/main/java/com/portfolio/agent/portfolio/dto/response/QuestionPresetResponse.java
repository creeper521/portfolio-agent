package com.portfolio.agent.portfolio.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portfolio.agent.portfolio.domain.QuestionDefinition;

import java.util.List;

public final class QuestionPresetResponse {

    private final String id;
    private final String text;
    private final String projectSlug;
    private final List<String> caseSlugs;
    private final List<String> audiences;
    private final List<String> placements;

    public QuestionPresetResponse(
            String id,
            String text,
            String projectSlug,
            List<String> caseSlugs,
            List<String> audiences,
            List<String> placements
    ) {
        this.id = id;
        this.text = text;
        this.projectSlug = projectSlug;
        this.caseSlugs = List.copyOf(caseSlugs);
        this.audiences = List.copyOf(audiences);
        this.placements = List.copyOf(placements);
    }

    public static QuestionPresetResponse from(QuestionDefinition definition, String projectSlug) {
        return from(definition, projectSlug, List.of());
    }

    public static QuestionPresetResponse from(
            QuestionDefinition definition,
            String projectSlug,
            List<String> caseSlugs
    ) {
        return new QuestionPresetResponse(
                definition.getId(),
                definition.getText(),
                projectSlug,
                caseSlugs,
                definition.getAudiences(),
                definition.getPlacements()
        );
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public String getProjectSlug() {
        return projectSlug;
    }

    public List<String> getCaseSlugs() {
        return caseSlugs;
    }

    public List<String> getAudiences() {
        return audiences;
    }

    public List<String> getPlacements() {
        return placements;
    }
}
