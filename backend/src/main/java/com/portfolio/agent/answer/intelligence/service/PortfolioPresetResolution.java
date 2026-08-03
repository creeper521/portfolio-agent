package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.intelligence.domain.AnswerIntentSource;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTask;

import java.util.Objects;

public final class PortfolioPresetResolution {

    private final PortfolioPresetResolutionType type;
    private final PortfolioTask task;
    private final AnswerIntentSource intentSource;
    private final String questionPresetId;

    private PortfolioPresetResolution(
            PortfolioPresetResolutionType type,
            PortfolioTask task,
            AnswerIntentSource intentSource,
            String questionPresetId
    ) {
        this.type = Objects.requireNonNull(type, "type");
        this.task = task;
        this.intentSource = intentSource;
        this.questionPresetId = questionPresetId;
    }

    public static PortfolioPresetResolution matched(
            PortfolioTask task,
            String questionPresetId
    ) {
        return new PortfolioPresetResolution(
                PortfolioPresetResolutionType.MATCHED,
                Objects.requireNonNull(task, "task"),
                AnswerIntentSource.PRESET,
                Objects.requireNonNull(questionPresetId, "questionPresetId"));
    }

    public static PortfolioPresetResolution noMatch() {
        return new PortfolioPresetResolution(
                PortfolioPresetResolutionType.NO_MATCH, null, null, null);
    }

    public static PortfolioPresetResolution invalid() {
        return new PortfolioPresetResolution(
                PortfolioPresetResolutionType.INVALID, null, null, null);
    }

    public PortfolioPresetResolutionType getType() { return type; }
    public PortfolioTask getTask() { return task; }
    public AnswerIntentSource getIntentSource() { return intentSource; }
    public String getQuestionPresetId() { return questionPresetId; }
}
