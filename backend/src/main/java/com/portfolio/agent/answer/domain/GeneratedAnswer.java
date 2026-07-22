package com.portfolio.agent.answer.domain;

import java.util.List;

public final class GeneratedAnswer {

    private final String title;
    private final String summary;
    private final List<AnswerSection> sections;

    public GeneratedAnswer(String title, String summary, List<AnswerSection> sections) {
        this.title = title;
        this.summary = summary;
        this.sections = List.copyOf(sections);
    }

    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public List<AnswerSection> getSections() { return sections; }
}
