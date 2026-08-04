package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.intelligence.domain.AnswerIntentSource;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTask;
import com.portfolio.agent.answer.intelligence.domain.PortfolioContractTask;

import java.util.Objects;

public final class PortfolioPresetResolution {

    private final PortfolioPresetResolutionType type;
    private final PortfolioTask task;
    private final PortfolioContractTask contractTask;
    private final AnswerIntentSource intentSource;
    private final String questionPresetId;
    private final String latestContractVersion;

    private PortfolioPresetResolution(
            PortfolioPresetResolutionType type,
            PortfolioTask task,
            PortfolioContractTask contractTask,
            AnswerIntentSource intentSource,
            String questionPresetId,
            String latestContractVersion
    ) {
        this.type = Objects.requireNonNull(type, "type");
        this.task = task;
        this.contractTask = contractTask;
        this.intentSource = intentSource;
        this.questionPresetId = questionPresetId;
        this.latestContractVersion = latestContractVersion;
    }

    public static PortfolioPresetResolution matched(
            PortfolioTask task,
            String questionPresetId
    ) {
        return new PortfolioPresetResolution(
                PortfolioPresetResolutionType.MATCHED,
                Objects.requireNonNull(task, "task"),
                null, AnswerIntentSource.PRESET,
                Objects.requireNonNull(questionPresetId, "questionPresetId"), null);
    }

    public static PortfolioPresetResolution matchedContract(PortfolioContractTask task) {
        return new PortfolioPresetResolution(
                PortfolioPresetResolutionType.MATCHED, null,
                Objects.requireNonNull(task, "task"), AnswerIntentSource.PRESET,
                task.getPresetId(), null);
    }

    public static PortfolioPresetResolution noMatch() {
        return new PortfolioPresetResolution(
                PortfolioPresetResolutionType.NO_MATCH, null, null, null, null, null);
    }

    public static PortfolioPresetResolution invalid() {
        return new PortfolioPresetResolution(
                PortfolioPresetResolutionType.INVALID, null, null, null, null, null);
    }

    public static PortfolioPresetResolution stale(String presetId, String latestContractVersion) {
        return new PortfolioPresetResolution(
                PortfolioPresetResolutionType.STALE, null, null, AnswerIntentSource.PRESET,
                Objects.requireNonNull(presetId, "presetId"),
                Objects.requireNonNull(latestContractVersion, "latestContractVersion"));
    }

    public static PortfolioPresetResolution unavailable(String presetId) {
        return new PortfolioPresetResolution(
                PortfolioPresetResolutionType.UNAVAILABLE, null, null, AnswerIntentSource.PRESET,
                Objects.requireNonNull(presetId, "presetId"), null);
    }

    public PortfolioPresetResolutionType getType() { return type; }
    public PortfolioTask getTask() { return task; }
    public PortfolioContractTask getContractTask() { return contractTask; }
    public AnswerIntentSource getIntentSource() { return intentSource; }
    public String getQuestionPresetId() { return questionPresetId; }
    public String getLatestContractVersion() { return latestContractVersion; }
}
