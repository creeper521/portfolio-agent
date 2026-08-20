package com.portfolio.agent.turn.capability.synthesis;

import com.portfolio.agent.turn.execution.AnswerSectionType;
import com.portfolio.agent.turn.execution.PublicSourceReferenceValue;
import com.portfolio.agent.turn.execution.TaskPresentation;

import java.util.List;
import java.util.Objects;

public final class CrossDomainPresentation implements TaskPresentation {
    private final String title;
    private final List<Section> sections;

    public CrossDomainPresentation(String title, List<Section> sections) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title is required");
        this.title = title.trim();
        this.sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
        if (this.sections.size() != 3) throw new IllegalArgumentException("three sections are required");
    }
    public String getTitle() { return title; }
    public List<Section> getSections() { return sections; }

    public record Section(
            AnswerSectionType sectionType, String title, String content,
            List<PublicSourceReferenceValue> sources) {
        public Section {
            Objects.requireNonNull(sectionType, "sectionType");
            if (title == null || title.isBlank() || content == null || content.isBlank()) {
                throw new IllegalArgumentException("section title/content are required");
            }
            title = title.trim();
            content = content.trim();
            sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        }
    }
}
