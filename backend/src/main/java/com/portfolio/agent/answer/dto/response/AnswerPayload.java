package com.portfolio.agent.answer.dto.response;

import java.util.List;
import java.util.Objects;

public final class AnswerPayload {

    private final String title;
    private final List<AnswerSectionResponse> sections;

    public AnswerPayload(String title, List<AnswerSectionResponse> sections) {
        this.title = title;
        this.sections = List.copyOf(sections);
    }

    public String getTitle() {
        return title;
    }

    public List<AnswerSectionResponse> getSections() {
        return sections;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AnswerPayload that)) {
            return false;
        }
        return Objects.equals(title, that.title) && Objects.equals(sections, that.sections);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, sections);
    }

    @Override
    public String toString() {
        return "AnswerPayload{" +
                "title='" + title + '\'' +
                ", sections=" + sections +
                '}';
    }
}
