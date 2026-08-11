package com.portfolio.agent.answer.domain;

import com.portfolio.agent.answer.exception.PortfolioAnswerCompositionException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PortfolioAnswerPlan {

    private final String title;
    private final String summary;
    private final List<PortfolioAnswerSection> sections;

    public PortfolioAnswerPlan(
            String title,
            String summary,
            List<PortfolioAnswerSection> sections) {
        this.title = requireText(title, "title");
        this.summary = summary == null || summary.isBlank() ? null : summary.trim();
        this.sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
        if (this.sections.isEmpty()) {
            throw new PortfolioAnswerCompositionException("sections must not be empty");
        }
        Set<AnswerSectionType> types = new HashSet<>();
        for (PortfolioAnswerSection section : this.sections) {
            if (!types.add(section.getSectionType())) {
                throw new PortfolioAnswerCompositionException(
                        "answer plan contains duplicate section type");
            }
            if (section.getSectionType() != AnswerSectionType.BOUNDARY
                    && section.getEvidenceIds().isEmpty()) {
                throw new PortfolioAnswerCompositionException(
                        "fact section requires at least one evidence");
            }
        }
    }

    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public List<PortfolioAnswerSection> getSections() { return sections; }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof PortfolioAnswerPlan that)) { return false; }
        return Objects.equals(title, that.title)
                && Objects.equals(summary, that.summary)
                && Objects.equals(sections, that.sections);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, summary, sections);
    }

    @Override
    public String toString() {
        return "PortfolioAnswerPlan{title='" + title + '\''
                + ", summary='" + summary + '\''
                + ", sectionCount=" + sections.size() + '}';
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
