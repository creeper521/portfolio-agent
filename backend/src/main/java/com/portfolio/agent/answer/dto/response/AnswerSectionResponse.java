package com.portfolio.agent.answer.dto.response;

import com.portfolio.agent.answer.domain.AnswerSectionType;

import java.util.Objects;

public final class AnswerSectionResponse {

    private final AnswerSectionType type;
    private final String content;

    public AnswerSectionResponse(AnswerSectionType type, String content) {
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
        if (!(other instanceof AnswerSectionResponse that)) {
            return false;
        }
        return type == that.type && Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, content);
    }

    @Override
    public String toString() {
        return "AnswerSectionResponse{" +
                "type=" + type +
                ", content='" + content + '\'' +
                '}';
    }
}
