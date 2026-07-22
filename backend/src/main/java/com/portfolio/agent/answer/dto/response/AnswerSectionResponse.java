package com.portfolio.agent.answer.dto.response;

import com.portfolio.agent.answer.domain.AnswerSectionType;

import java.util.Objects;
import java.util.List;

public final class AnswerSectionResponse {

    private final AnswerSectionType type;
    private final String title;
    private final String content;
    private final List<String> evidenceIds;
    private final List<String> claimIds;

    public AnswerSectionResponse(
            AnswerSectionType type,
            String title,
            String content,
            List<String> evidenceIds,
            List<String> claimIds
    ) {
        this.type = type;
        this.title = title;
        this.content = content;
        this.evidenceIds = List.copyOf(evidenceIds);
        this.claimIds = List.copyOf(claimIds);
    }

    public AnswerSectionResponse(AnswerSectionType type, String title, String content,
            List<String> evidenceIds) {
        this(type, title, content, evidenceIds, List.of());
    }

    public AnswerSectionType getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public String getTitle() { return title; }
    public List<String> getEvidenceIds() { return evidenceIds; }
    public List<String> getClaimIds() { return claimIds; }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AnswerSectionResponse that)) {
            return false;
        }
        return type == that.type
                && Objects.equals(title, that.title)
                && Objects.equals(content, that.content)
                && Objects.equals(evidenceIds, that.evidenceIds)
                && Objects.equals(claimIds, that.claimIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, title, content, evidenceIds, claimIds);
    }

    @Override
    public String toString() {
        return "AnswerSectionResponse{" +
                "type=" + type +
                ", title='" + title + '\'' +
                ", content='" + content + '\'' +
                ", evidenceIds=" + evidenceIds +
                ", claimIds=" + claimIds +
                '}';
    }
}
