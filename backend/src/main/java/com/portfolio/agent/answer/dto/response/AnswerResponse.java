package com.portfolio.agent.answer.dto.response;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.domain.VerificationStatus;

import java.util.List;

public final class AnswerResponse {

    private final String requestId;
    private final String turnId;
    private final String contentVersion;
    private final String questionPresetId;
    private final AnswerResolution resolution;
    private final AnswerSource answerSource;
    private final GenerationMode generationMode;
    private final VerificationStatus verification;
    private final String title;
    private final String summary;
    private final List<AnswerSectionResponse> sections;
    private final List<String> evidenceIds;
    private final List<String> suggestedQuestionPresetIds;

    public AnswerResponse(
            String requestId,
            String turnId,
            String contentVersion,
            String questionPresetId,
            AnswerResolution resolution,
            AnswerSource answerSource,
            GenerationMode generationMode,
            VerificationStatus verification,
            String title,
            String summary,
            List<AnswerSectionResponse> sections,
            List<String> evidenceIds,
            List<String> suggestedQuestionPresetIds
    ) {
        this.requestId = requestId;
        this.turnId = turnId;
        this.contentVersion = contentVersion;
        this.questionPresetId = questionPresetId;
        this.resolution = resolution;
        this.answerSource = answerSource;
        this.generationMode = generationMode;
        this.verification = verification;
        this.title = title;
        this.summary = summary;
        this.sections = List.copyOf(sections);
        this.evidenceIds = List.copyOf(evidenceIds);
        this.suggestedQuestionPresetIds = List.copyOf(suggestedQuestionPresetIds);
    }

    public String getRequestId() { return requestId; }
    public String getTurnId() { return turnId; }
    public String getContentVersion() { return contentVersion; }
    public String getQuestionPresetId() { return questionPresetId; }
    public AnswerResolution getResolution() { return resolution; }
    public AnswerSource getAnswerSource() { return answerSource; }
    public GenerationMode getGenerationMode() { return generationMode; }
    public VerificationStatus getVerification() { return verification; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public List<AnswerSectionResponse> getSections() { return sections; }
    public List<String> getEvidenceIds() { return evidenceIds; }
    public List<String> getSuggestedQuestionPresetIds() { return suggestedQuestionPresetIds; }
}
