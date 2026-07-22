package com.portfolio.agent.answer.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Objects;

public final class AnswerRequest {

    @NotBlank(message = "turnId is required")
    @Size(max = 100, message = "turnId must not exceed 100 characters")
    private final String turnId;

    @Pattern(regexp = "[a-z0-9-]{1,100}", message = "questionPresetId format is invalid")
    private final String questionPresetId;

    @Size(max = 500, message = "question must not exceed 500 characters")
    private final String question;

    @Valid
    @NotNull(message = "context is required")
    private final AnswerContextRequest context;

    @JsonCreator
    public AnswerRequest(
            @JsonProperty("turnId") String turnId,
            @JsonProperty("questionPresetId") String questionPresetId,
            @JsonProperty("question") String question,
            @JsonProperty("context") AnswerContextRequest context
    ) {
        this.turnId = turnId;
        this.questionPresetId = questionPresetId;
        this.question = question;
        this.context = context;
    }

    public String getTurnId() {
        return turnId;
    }

    public String getQuestionPresetId() {
        return questionPresetId;
    }

    public String getQuestion() {
        return question;
    }

    public AnswerContextRequest getContext() {
        return context;
    }

    @AssertTrue(message = "questionPresetId or question is required")
    public boolean isQuestionSelectionValid() {
        return hasText(questionPresetId) || hasText(question);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AnswerRequest that)) {
            return false;
        }
        return Objects.equals(turnId, that.turnId)
                && Objects.equals(questionPresetId, that.questionPresetId)
                && Objects.equals(question, that.question)
                && Objects.equals(context, that.context);
    }

    @Override
    public int hashCode() {
        return Objects.hash(turnId, questionPresetId, question, context);
    }

    @Override
    public String toString() {
        return "AnswerRequest{" +
                "turnId='" + turnId + '\'' +
                ", questionPresetId='" + questionPresetId + '\'' +
                ", question='<redacted>'" +
                '}';
    }
}
