package com.portfolio.agent.answer.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.portfolio.agent.answer.domain.ConversationMessageRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class ConversationAnswerRequest {

    @NotBlank(message = "turnId is required")
    @Size(max = 100, message = "turnId must not exceed 100 characters")
    private final String turnId;

    @NotBlank(message = "question is required")
    @Size(max = 2000, message = "question must not exceed 2000 characters")
    private final String question;

    @Valid
    @NotNull(message = "messages are required")
    @Size(max = 40, message = "messages must contain at most 20 rounds")
    private final List<ConversationMessageRequest> messages;

    @Valid
    @NotNull(message = "context is required")
    private final ConversationAnswerContextRequest context;

    @JsonCreator
    public ConversationAnswerRequest(
            @JsonProperty("turnId") String turnId,
            @JsonProperty("question") String question,
            @JsonProperty("messages") List<ConversationMessageRequest> messages,
            @JsonProperty("context") ConversationAnswerContextRequest context
    ) {
        this.turnId = turnId;
        this.question = question;
        this.messages = messages == null ? List.of() : List.copyOf(messages);
        this.context = context;
    }

    public String getTurnId() { return turnId; }
    public String getQuestion() { return question; }
    public List<ConversationMessageRequest> getMessages() { return messages; }
    public ConversationAnswerContextRequest getContext() { return context; }

    @AssertTrue(message = "messages must alternate USER and ASSISTANT")
    public boolean isMessageOrderValid() {
        for (int index = 0; index < messages.size(); index++) {
            ConversationMessageRole expected = index % 2 == 0
                    ? ConversationMessageRole.USER
                    : ConversationMessageRole.ASSISTANT;
            if (messages.get(index).getRole() != expected) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "ConversationAnswerRequest{" +
                "turnId='" + turnId + '\'' +
                ", question='<redacted>'" +
                ", messageCount=" + messages.size() +
                '}';
    }
}
