package com.portfolio.agent.answer.domain.model;

import java.util.Objects;

public final class AnswerSection {

    private final AnswerSectionType type;
    private final String content;

    public AnswerSection(AnswerSectionType type, String content) {
        this.type = type;
        this.content = content;
    }

    public AnswerSectionType getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AnswerSection that)) {
            return false;
        }
        return Objects.equals(type, that.type)
                && Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, content);
    }

    @Override
    public String toString() {
        return "AnswerSection{" +
                "type=" + type +
                ", content='" + content + '\'' +
                '}';
    }
}
