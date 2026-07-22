package com.portfolio.agent.answer.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public final class FeedbackSignal {

    private final String contentVersion;
    private final String questionPresetId;
    private final AnswerResolution resolution;
    private final AnswerSource answerSource;
    private final boolean helpful;
    private final FeedbackReason reason;

    @JsonCreator
    public FeedbackSignal(
            @JsonProperty("contentVersion") String contentVersion,
            @JsonProperty("questionPresetId") String questionPresetId,
            @JsonProperty("resolution") AnswerResolution resolution,
            @JsonProperty("answerSource") AnswerSource answerSource,
            @JsonProperty("helpful") boolean helpful,
            @JsonProperty("reason") FeedbackReason reason
    ) {
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
        this.questionPresetId = Objects.requireNonNull(questionPresetId, "questionPresetId");
        this.resolution = Objects.requireNonNull(resolution, "resolution");
        this.answerSource = Objects.requireNonNull(answerSource, "answerSource");
        this.helpful = helpful;
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public String getContentVersion() { return contentVersion; }
    public String getQuestionPresetId() { return questionPresetId; }
    public AnswerResolution getResolution() { return resolution; }
    public AnswerSource getAnswerSource() { return answerSource; }
    public boolean isHelpful() { return helpful; }
    public FeedbackReason getReason() { return reason; }
}
