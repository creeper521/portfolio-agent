package com.portfolio.agent.answer.domain;

import java.util.Objects;

public final class ConversationSubjectOption {

    private final AnswerSubjectType subjectType;
    private final String slug;
    private final String title;
    private final String summary;

    public ConversationSubjectOption(
            AnswerSubjectType subjectType,
            String slug,
            String title,
            String summary
    ) {
        this.subjectType = Objects.requireNonNull(subjectType, "subjectType");
        this.slug = Objects.requireNonNull(slug, "slug");
        this.title = Objects.requireNonNull(title, "title");
        this.summary = summary;
    }

    public AnswerSubjectType getSubjectType() { return subjectType; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
}
