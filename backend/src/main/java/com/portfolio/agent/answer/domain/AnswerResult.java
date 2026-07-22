package com.portfolio.agent.answer.domain;

import java.util.List;

public final class AnswerResult {

    private final AnswerTurnSnapshot turnSnapshot;
    private final AnswerResolution resolution;
    private final AnswerSource answerSource;
    private final GenerationMode generationMode;
    private final VerificationStatus verification;
    private final String title;
    private final String summary;
    private final List<AnswerSection> sections;
    private final List<String> evidenceIds;
    private final List<String> suggestedQuestionPresetIds;
    private final boolean contextVersionUpdated;

    public AnswerResult(
            AnswerTurnSnapshot turnSnapshot,
            AnswerResolution resolution,
            AnswerSource answerSource,
            GenerationMode generationMode,
            VerificationStatus verification,
            String title,
            String summary,
            List<AnswerSection> sections,
            List<String> evidenceIds,
            List<String> suggestedQuestionPresetIds
    ) {
        this(turnSnapshot, resolution, answerSource, generationMode, verification,
                title, summary, sections, evidenceIds, suggestedQuestionPresetIds, false);
    }

    public AnswerResult(
            AnswerTurnSnapshot turnSnapshot,
            AnswerResolution resolution,
            AnswerSource answerSource,
            GenerationMode generationMode,
            VerificationStatus verification,
            String title,
            String summary,
            List<AnswerSection> sections,
            List<String> evidenceIds,
            List<String> suggestedQuestionPresetIds,
            boolean contextVersionUpdated
    ) {
        this.turnSnapshot = turnSnapshot;
        this.resolution = resolution;
        this.answerSource = answerSource;
        this.generationMode = generationMode;
        this.verification = verification;
        this.title = title;
        this.summary = summary;
        this.sections = List.copyOf(sections);
        this.evidenceIds = List.copyOf(evidenceIds);
        this.suggestedQuestionPresetIds = List.copyOf(suggestedQuestionPresetIds);
        this.contextVersionUpdated = contextVersionUpdated;
    }

    public AnswerTurnSnapshot getTurnSnapshot() { return turnSnapshot; }
    public AnswerResolution getResolution() { return resolution; }
    public AnswerSource getAnswerSource() { return answerSource; }
    public GenerationMode getGenerationMode() { return generationMode; }
    public VerificationStatus getVerification() { return verification; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public List<AnswerSection> getSections() { return sections; }
    public List<String> getEvidenceIds() { return evidenceIds; }
    public List<String> getSuggestedQuestionPresetIds() { return suggestedQuestionPresetIds; }
    public boolean isContextVersionUpdated() { return contextVersionUpdated; }
}
