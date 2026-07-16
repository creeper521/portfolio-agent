package com.portfolio.agent.answer.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Objects;

public final class AnswerRequest {

    @NotBlank(message = "projectSlug is required")
    @Pattern(regexp = "[a-z0-9-]{1,64}", message = "projectSlug format is invalid")
    private final String projectSlug;

    @NotBlank(message = "question is required")
    @Size(max = 500, message = "question must not exceed 500 characters")
    private final String question;

    @JsonCreator
    public AnswerRequest(
            @JsonProperty("projectSlug") String projectSlug,
            @JsonProperty("question") String question
    ) {
        this.projectSlug = projectSlug;
        this.question = question;
    }

    public String getProjectSlug() {
        return projectSlug;
    }

    public String getQuestion() {
        return question;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AnswerRequest that)) {
            return false;
        }
        return Objects.equals(projectSlug, that.projectSlug)
                && Objects.equals(question, that.question);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectSlug, question);
    }

    @Override
    public String toString() {
        return "AnswerRequest{" +
                "projectSlug='" + projectSlug + '\'' +
                ", question='" + question + '\'' +
                '}';
    }
}
