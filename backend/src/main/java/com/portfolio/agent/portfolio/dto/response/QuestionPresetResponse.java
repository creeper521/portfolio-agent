package com.portfolio.agent.portfolio.dto.response;

import com.portfolio.agent.portfolio.domain.QuestionDefinition;

import java.util.List;

public final class QuestionPresetResponse {

    private final String id;
    private final String text;
    private final String projectSlug;
    private final List<String> audiences;
    private final List<String> placements;

    public QuestionPresetResponse(
            String id,
            String text,
            String projectSlug,
            List<String> audiences,
            List<String> placements
    ) {
        this.id = id;
        this.text = text;
        this.projectSlug = projectSlug;
        this.audiences = List.copyOf(audiences);
        this.placements = List.copyOf(placements);
    }

    public static QuestionPresetResponse from(QuestionDefinition definition, String projectSlug) {
        return new QuestionPresetResponse(
                definition.getId(),
                definition.getText(),
                projectSlug,
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

    public String getProjectSlug() {
        return projectSlug;
    }

    public List<String> getAudiences() {
        return audiences;
    }

    public List<String> getPlacements() {
        return placements;
    }
}
