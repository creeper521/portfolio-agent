package com.portfolio.agent.turn.execution;

import com.portfolio.agent.turn.execution.AnswerSectionType;
import com.portfolio.agent.turn.execution.PublicSourceReferenceValue;

import java.util.List;
import java.util.Objects;

public final class SectionedTaskPresentation implements TaskPresentation {
    private final String summary;
    private final List<Section> sections;

    public SectionedTaskPresentation(String summary, List<Section> sections) {
        this.summary = summary == null || summary.isBlank() ? null : summary.trim();
        this.sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
        if (this.sections.isEmpty()) throw new IllegalArgumentException("sections must not be empty");
    }

    public String getSummary() { return summary; }
    public List<Section> getSections() { return sections; }

    public static final class Section {
        private final AnswerSectionType sectionType;
        private final String title;
        private final String content;
        private final List<PublicSourceReferenceValue> sourceReferences;
        public Section(
                AnswerSectionType sectionType, String title, String content,
                List<PublicSourceReferenceValue> sourceReferences) {
            this.sectionType = Objects.requireNonNull(sectionType, "sectionType");
            if (title == null || title.isBlank() || content == null || content.isBlank()) {
                throw new IllegalArgumentException("section title and content are required");
            }
            this.title = title.trim();
            this.content = content.trim();
            this.sourceReferences = List.copyOf(
                    Objects.requireNonNull(sourceReferences, "sourceReferences"));
        }
        public AnswerSectionType getSectionType() { return sectionType; }
        public String getTitle() { return title; }
        public String getContent() { return content; }
        public List<PublicSourceReferenceValue> getSourceReferences() { return sourceReferences; }
    }
}
