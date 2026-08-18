package com.portfolio.agent.turn.capability.general;

import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.turn.execution.TaskPresentation;

import java.util.List;
import java.util.Objects;

public final class GeneralPresentation implements TaskPresentation {
    private final String title;
    private final List<Section> sections;

    public GeneralPresentation(String title, List<Section> sections) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title is required");
        this.title = title.trim();
        this.sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
        if (this.sections.isEmpty()) throw new IllegalArgumentException("sections are required");
    }
    public String getTitle() { return title; }
    public List<Section> getSections() { return sections; }

    public record Section(AnswerSectionType sectionType, String title, String content) {
        public Section {
            Objects.requireNonNull(sectionType, "sectionType");
            if (title == null || title.isBlank() || content == null || content.isBlank()) {
                throw new IllegalArgumentException("section title/content are required");
            }
            title = title.trim();
            content = content.trim();
        }
    }
}
