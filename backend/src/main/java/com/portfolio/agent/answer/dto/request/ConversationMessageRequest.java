package com.portfolio.agent.answer.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.portfolio.agent.answer.domain.ConversationMessageRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ConversationMessageRequest {

    @NotNull(message = "message role is required")
    private final ConversationMessageRole role;

    @NotBlank(message = "message content is required")
    @Size(max = 4000, message = "message content must not exceed 4000 characters")
    private final String content;

    @JsonCreator
    public ConversationMessageRequest(
            @JsonProperty("role") ConversationMessageRole role,
            @JsonProperty("content") String content
    ) {
        this.role = role;
        this.content = content;
    }

    public ConversationMessageRole getRole() { return role; }
    public String getContent() { return content; }

    @Override
    public String toString() {
        return "ConversationMessageRequest{" +
                "role=" + role +
                ", content='<redacted>'" +
                '}';
    }
}
